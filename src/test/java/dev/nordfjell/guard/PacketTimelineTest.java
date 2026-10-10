package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PacketTimelineTest {
    @Test void worldDataMaintainsFifoAndAtMostOneChunkPerDrain() {
        var q=new PacketInbox(bytes->true);var seen=new java.util.ArrayList<WorldSnapshot.Update>();
        var t=new PacketTimeline(seen::add);
        var a=new WorldSnapshot.EncodedChunk(0,0,new byte[]{1});
        var b=new WorldSnapshot.EncodedChunk(1,0,new byte[]{2});
        var block=new WorldSnapshot.Blocks(new int[]{0,80,0,2});
        q.world(a);q.world(block);q.world(b);q.event(NativePackets.INPUT,1,0,7,0,0,0,0,0);
        assertEquals(2,t.drain(q,100));assertEquals(java.util.List.of(a,block),seen);assertEquals(0,t.input());
        assertEquals(2,t.drain(q,200));assertEquals(java.util.List.of(a,block,b),seen);assertEquals(7,t.input());
    }
    @Test void worldEventLimitAndCopyBudgetPreventUnboundedBacklog() {
        var q=new PacketInbox(bytes->true);assertTrue(q.worldEnabled());assertFalse(new PacketInbox().worldEnabled());
        for(int i=0;i<16;i++)q.world(new WorldSnapshot.Forget(i,0));
        assertFalse(q.reserveWorldCopy(48));q.world(new WorldSnapshot.Reset());assertEquals(1,q.dropped());
        var c=new PacketInbox.Cursor();for(int i=0;i<16;i++)assertTrue(q.poll(c));
        assertTrue(q.reserveWorldCopy(512*1024));assertFalse(q.reserveWorldCopy(512*1024+1));
        var denied=new PacketInbox(bytes->false);assertFalse(denied.reserveWorldCopy(48));
    }
    @Test void queueOverflowInvalidatesWorldBeforeDiscardingEntirePrefix() {
        var q=new PacketInbox(bytes->true);var seen=new java.util.ArrayList<WorldSnapshot.Update>();
        var t=new PacketTimeline(seen::add);q.world(new WorldSnapshot.Forget(0,0));
        for(int i=0;i<300;i++)q.event(NativePackets.MOVE,i,0,1,i,80,0,0,0);
        assertEquals(128,t.drain(q,1000));assertEquals(1,seen.size());
        assertInstanceOf(WorldSnapshot.Invalidation.class,seen.getFirst());
        assertEquals(128,t.drain(q,1001));assertEquals(0,t.historySize());assertEquals(1,seen.size());
    }
    private static PacketInbox.Cursor event(int kind, long time, int id) {
        var c = new PacketInbox.Cursor(); c.kind = kind; c.nano = time; c.id = id; return c;
    }
    private static void attach(PacketTimeline t) { t.accept(event(NativePackets.ATTACHED, 1, 0)); }

    @Test void selfContextStillInvalidatesPhysicsConfidence() {
        var t=new PacketTimeline();attach(t);
        t.accept(event(NativePackets.BARRIER_SENT,100,7));t.accept(event(NativePackets.PONG,200,7));
        assertTrue(t.prefixAcknowledged());
        t.accept(event(NativePackets.PLAYER_CONTEXT,300,0));assertFalse(t.prefixAcknowledged());
    }
    @Test void queueBoundAndWraparound() {
        var q = new PacketInbox(); var c = new PacketInbox.Cursor();
        for (int round = 0; round < 4; round++) {
            for (int i = 0; i < 256; i++) q.event(3, i, round, i, i, -i, i * 2, i, -i);
            q.event(0, 0, 0, 0, 0, 0, 0, 0, 0);
            assertEquals(round + 1, q.dropped()); assertEquals(256, q.size());
            for (int i = 0; i < 256; i++) {
                assertTrue(q.poll(c)); assertEquals(i, c.flags); assertEquals(round, c.id);
                assertEquals(-i, c.y); assertEquals(i * 2, c.z); assertEquals(-i, c.pitch);
            }
            assertFalse(q.poll(c));
        }
    }
    @Test void crossThreadPublicationDoesNotTear() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var q = new PacketInbox(); var failure = new AtomicReference<Throwable>();
            Thread producer = Thread.ofPlatform().daemon().start(() -> {
                try {
                    for (int i = 1; i <= 100_000; i++) {
                        while (q.size() == PacketInbox.CAPACITY) Thread.onSpinWait();
                        q.event(1, i, i, i, i, -i, i * 2, i, -i);
                    }
                } catch (Throwable e) { failure.set(e); }
            });
            var c = new PacketInbox.Cursor();
            for (int i = 1; i <= 100_000; i++) {
                while (!q.poll(c)) { if (failure.get() != null) fail(failure.get()); Thread.onSpinWait(); }
                assertEquals(i, c.nano); assertEquals(i, c.id); assertEquals(i, c.x);
                assertEquals(-i, c.y); assertEquals(i * 2, c.z); assertEquals(i, c.yaw);
            }
            producer.join(1000); assertFalse(producer.isAlive()); assertEquals(0, q.dropped());
        });
    }
    @Test void knownOrderedBarrierAcknowledgesOnlyItsOutboundPrefix() {
        var t = new PacketTimeline(); attach(t);
        t.accept(event(NativePackets.BARRIER_SENT, 100, 7));
        t.accept(event(NativePackets.PONG, 200, 99)); assertEquals(0, t.acknowledgements());
        t.accept(event(NativePackets.PONG, 300, 7));
        assertTrue(t.prefixAcknowledged()); assertEquals(200, t.latencyNanos());
        t.accept(event(NativePackets.WORLD_CHANGE, 400, 0)); assertFalse(t.prefixAcknowledged());
        t.accept(event(NativePackets.PONG, 500, 7)); assertEquals(1, t.acknowledgements());
    }
    @Test void expiryAndReorderedAcksCannotEstablishConfidence() {
        var t = new PacketTimeline(); attach(t);
        t.accept(event(NativePackets.BARRIER_SENT, 10, 1));
        t.accept(event(NativePackets.BARRIER_SENT, 20, 2));
        t.accept(event(NativePackets.PONG, 30, 2)); assertFalse(t.prefixAcknowledged());
        assertEquals(1, t.resets());
        t.accept(event(NativePackets.BARRIER_SENT, 40, 3)); t.expire(40 + PacketTimeline.TIMEOUT + 1);
        t.accept(event(NativePackets.PONG, 50 + PacketTimeline.TIMEOUT, 3));
        assertFalse(t.prefixAcknowledged()); assertEquals(2, t.resets());
    }
    @Test void teleportRequiresMatchingAcknowledgement() {
        var t = new PacketTimeline(); attach(t);
        t.accept(event(NativePackets.TELEPORT, 20, 7));
        t.accept(event(NativePackets.TELEPORT_ACK, 30, 6)); assertTrue(t.teleportPending());
        t.accept(event(NativePackets.TELEPORT_ACK, 40, 7)); assertFalse(t.teleportPending());
        assertFalse(t.prefixAcknowledged());
    }
    @Test void inputPersistsAcrossTickWithoutMovement() {
        var t = new PacketTimeline(); var c = event(NativePackets.INPUT, 20, 0); c.flags = 65; t.accept(c);
        for (int i = 1; i <= 20; i++) t.accept(event(NativePackets.TICK_END, i * 50_000_000L, 0));
        assertEquals(65, t.input()); assertEquals(20, t.ticks()); assertEquals(0, t.historySize());
        assertEquals(0, t.timerExcess());
    }
    @Test void fasterClaimedTicksDoNotEarnCreditFromWorldUpdates() {
        var t = new PacketTimeline();
        for (int i = 1; i <= 200; i++) {
            t.accept(event(NativePackets.TICK_END, i * 1_000_000L, 0));
            t.accept(event(NativePackets.WORLD_CHANGE, i * 1_000_000L, 0));
        }
        assertTrue(t.timerExcess() > 150);
    }
    @Test void shortDelayedNetworkBatchWithinBurstDoesNotFlagTimer() {
        var t = new PacketTimeline();
        for (int i = 0; i < 20; i++) t.accept(event(NativePackets.TICK_END, 1000 + i, 0));
        assertEquals(0, t.timerExcess());
    }
    @Test void overflowDiscardsWholeStalePrefixWithinWorkCap() {
        var q = new PacketInbox(); var t = new PacketTimeline(); attach(t);
        for (int i = 0; i < 300; i++) q.event(NativePackets.MOVE, i, 0, 1, i, 0, 0, 0, 0);
        assertEquals(128, t.drain(q, 1000)); assertEquals(128, t.drain(q, 1001));
        assertEquals(0, t.historySize()); assertEquals(1, t.resets());
        q.event(NativePackets.MOVE, 2000, 0, 1, 1, 2, 3, 0, 0); t.drain(q, 2001);
        assertEquals(1, t.historySize());
    }
    @Test void nonFinitePositionResetsHistoryAndHistoryStaysBounded() {
        var t = new PacketTimeline();
        for (int i = 0; i < 1000; i++) {
            var p = event(NativePackets.MOVE, i, 0); p.flags = 1; p.x = i; t.accept(p);
        }
        assertEquals(64, t.historySize()); assertEquals(10, t.diagnostic().length);
        var p = event(NativePackets.MOVE, 1001, 0); p.flags = 1; p.x = Double.NaN; t.accept(p);
        assertEquals(0, t.historySize()); assertEquals(1, t.resets());
    }
}
