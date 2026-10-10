package dev.nordfjell.guard;

import java.util.Locale;

/** Entity-owner only. Acknowledgement is not evidence that the client obeyed the state. */
final class PacketTimeline {
    static final long TIMEOUT = 5_000_000_000L;
    static final int HISTORY = 64, MAX_DRAIN = 128;
    private final PacketInbox.Cursor cursor = new PacketInbox.Cursor();
    private final int[] pingIds = new int[4];
    private final long[] pingNanos = new long[4], pingRevisions = new long[4];
    private final long[] historyNanos = new long[HISTORY];
    private final double[] historyX = new double[HISTORY], historyY = new double[HISTORY], historyZ = new double[HISTORY];
    private final int[] historyFlags = new int[HISTORY];
    private long dropped, revision, acknowledgedRevision = -1, lastPing, lastTick, tickSequence, historySequence;
    private long latency, events, moves, acks, resets, timerExcess, malformed;
    private long teleports, teleportAcks, impulses;
    private int pending, teleportId, input;
    private boolean attached, awaitingTeleport, positioned, discarding;
    private double x, y, z, velocityX, velocityY, velocityZ, tickCredit = 40;
    private String reason = "joining";
    private final java.util.function.Consumer<WorldSnapshot.Update> worldSink;
    private final java.util.function.Consumer<PacketInbox.Cursor> eventSink;
    PacketTimeline() {this(update -> {});}
    PacketTimeline(java.util.function.Consumer<WorldSnapshot.Update> worldSink) {this(worldSink,null);}
    PacketTimeline(java.util.function.Consumer<WorldSnapshot.Update> worldSink,
                   java.util.function.Consumer<PacketInbox.Cursor> eventSink) {this.worldSink=worldSink;this.eventSink=eventSink;}

    int drain(PacketInbox inbox, long now) {
        if (inbox.dropped() != dropped) {
            dropped = inbox.dropped(); invalidate("inbox overflow");
            worldSink.accept(new WorldSnapshot.Invalidation());
            discarding = true;
        }
        if (discarding) {
            // May span two scheduler ticks; never process the remainder of a lost prefix.
            int count = 0; while (count < MAX_DRAIN && inbox.poll(cursor)) count++;
            if (inbox.size() == 0) discarding = false;
            reason = "inbox overflow"; return count;
        }
        int count = 0, chunks = 0;
        while (count < MAX_DRAIN) {
            if(inbox.nextIsEncodedChunk() && chunks>=1) break;
            if(!inbox.poll(cursor))break;
            if(cursor.payload instanceof WorldSnapshot.EncodedChunk)chunks++;
            accept(cursor);count++;
        }
        expire(now);
        return count;
    }

    void accept(PacketInbox.Cursor p) {
        if(eventSink!=null)eventSink.accept(p);
        events++;
        if(p.kind==NativePackets.WORLD_DATA) {worldSink.accept(p.payload);return;}
        if (p.kind == NativePackets.ATTACHED) { attached = true; reason = "awaiting barrier"; }
        else if (p.kind == NativePackets.CLOSED) { attached = false; invalidate("observer unavailable"); }
        else if (p.kind == NativePackets.WORLD_CHANGE || p.kind == NativePackets.CONTEXT_CHANGE) invalidate("outbound state changed");
        else if (p.kind == NativePackets.BARRIER_SENT) {
            if (pending == pingIds.length) { invalidate("barrier capacity"); }
            for (int i = 0; i < pending; i++) if (pingIds[i] == p.id) { invalidate("barrier ID collision"); return; }
            pingIds[pending] = p.id; pingNanos[pending] = p.nano; pingRevisions[pending++] = revision;
            lastPing = p.nano;
        } else if (p.kind == NativePackets.PONG) {
            int found = -1;
            for (int i = 0; i < pending; i++) if (pingIds[i] == p.id) { found = i; break; }
            if (found < 0) return; // Do not consume another plugin's Ping/Pong protocol.
            if (found != 0 || p.nano < pingNanos[found] || p.nano - pingNanos[found] > TIMEOUT) {
                invalidate("out-of-order or expired barrier"); return;
            }
            latency = p.nano - pingNanos[found]; acknowledgedRevision = pingRevisions[found]; acks++;
            System.arraycopy(pingIds, 1, pingIds, 0, --pending);
            System.arraycopy(pingNanos, 1, pingNanos, 0, pending);
            System.arraycopy(pingRevisions, 1, pingRevisions, 0, pending);
            reason = acknowledgedRevision == revision ? "prefix acknowledged (not physics validation)" : "newer state pending";
        } else if (p.kind == NativePackets.TELEPORT) {
            teleports++;
            invalidate("teleport pending"); teleportId = p.id; awaitingTeleport = true;
        } else if (p.kind == NativePackets.TELEPORT_ACK) {
            if (awaitingTeleport && teleportId == p.id) { teleportAcks++; awaitingTeleport = false; reason = "teleport acknowledged"; }
        } else if (p.kind == NativePackets.VELOCITY) {
            impulses++;
            invalidate("server impulse pending"); velocityX = p.x; velocityY = p.y; velocityZ = p.z;
        } else if (p.kind == NativePackets.INPUT) input = p.flags;
        else if (p.kind == NativePackets.TICK_END) {
            tickSequence++;
            if (lastTick != 0 && p.nano >= lastTick && p.nano - lastTick <= 2_000_000_000L) {
                tickCredit = Math.min(40, tickCredit + (p.nano - lastTick) / 50_000_000.0);
                if (tickCredit < 1) timerExcess++; else tickCredit--;
            } else tickCredit = 40;
            lastTick = p.nano;
        } else if (p.kind == NativePackets.MOVE) {
            moves++;
            if (((p.flags & 1) != 0 && (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)))
                    || ((p.flags & 2) != 0 && (!Float.isFinite(p.yaw) || !Float.isFinite(p.pitch)))) {
                malformed++; invalidate("non-finite movement"); return;
            }
            if ((p.flags & 1) != 0) { x = p.x; y = p.y; z = p.z; positioned = true; }
            int slot = (int) historySequence++ & (HISTORY - 1);
            historyNanos[slot] = p.nano; historyX[slot] = x; historyY[slot] = y; historyZ[slot] = z;
            historyFlags[slot] = p.flags | (positioned ? 16 : 0);
        }
    }

    void invalidate(String why) {
        revision++; acknowledgedRevision = -1; resets++; reason = why;
        pending = 0; positioned = false; historySequence = 0;
        velocityX = velocityY = velocityZ = 0;
        // Tick rate is independent of scene validity: state updates must not buy Timer credit.
    }
    void expire(long now) {
        if (pending > 0 && now - pingNanos[0] > TIMEOUT) invalidate("barrier timed out");
    }
    boolean shouldProbe(long now) { return attached && pending < 4 && (lastPing == 0 || now - lastPing >= 500_000_000L); }
    void transportActive(boolean active) { attached = active; }
    void probeQueued(long now) { lastPing = now; }
    long events() { return events; }
    long timerExcess() { return timerExcess; }
    long latencyNanos() { return latency; }
    long acknowledgements() { return acks; }
    long resets() { return resets; }
    boolean teleportPending() { return awaitingTeleport; }
    int pendingTeleportId() { return teleportId; }
    boolean prefixAcknowledged() { return attached && acknowledgedRevision == revision; }
    int input() { return input; }
    long ticks() { return tickSequence; }
    long historySize() { return Math.min(historySequence, HISTORY); }
    String[] diagnostic() {
        String[] lines = new String[(int) Math.min(historySequence, 8) + 2];
        lines[0] = "Packet observer=" + attached + ", events=" + events + ", moves=" + moves + ", client ticks=" + tickSequence
                + ", timer excess=" + timerExcess + " (diagnostic only), invalid=" + malformed
                + ", teleports=" + teleports + ", teleport acknowledgements=" + teleportAcks + ", impulses=" + impulses;
        lines[1] = "Barrier RTT ms=" + latency / 1_000_000 + ", acks=" + acks + ", pending=" + pending
                + ", resets=" + resets + ", dropped=" + dropped + ", teleport=" + awaitingTeleport + ", input=" + input + ", state=" + reason;
        for (int i = 2; i < lines.length; i++) {
            int slot = (int) (historySequence - (lines.length - 2) + i - 2) & (HISTORY - 1);
            lines[i] = String.format(Locale.ROOT, "Movement t=%d xyz=%.5f,%.5f,%.5f flags=%d",
                    historyNanos[slot], historyX[slot], historyY[slot], historyZ[slot], historyFlags[slot]);
        }
        return lines;
    }
}
