package dev.nordfjell.guard;

/** One channel-event-loop producer, one entity-scheduler consumer. No packet objects retained. */
final class PacketInbox implements NativePackets.Sink {
    static final int CAPACITY = 256;
    private final int[] kinds = new int[CAPACITY], ids = new int[CAPACITY], flags = new int[CAPACITY];
    private final long[] nanos = new long[CAPACITY];
    private final double[] xs = new double[CAPACITY], ys = new double[CAPACITY], zs = new double[CAPACITY];
    private final float[] yaws = new float[CAPACITY], pitches = new float[CAPACITY];
    private final WorldSnapshot.Update[] payloads = new WorldSnapshot.Update[CAPACITY];
    private final java.util.function.IntPredicate copyBudget;
    private final boolean worldEnabled;
    private long worldWritten;
    private volatile long worldRead;
    private long worldEventsWritten;
    private volatile long worldEventsRead;
    private volatile long written, read, dropped;
    PacketInbox() { copyBudget=bytes -> false;worldEnabled=false; }
    PacketInbox(java.util.function.IntPredicate copyBudget) { this.copyBudget=copyBudget;worldEnabled=true; }
    @Override public boolean worldEnabled() {return worldEnabled;}
    @Override public boolean reserveWorldCopy(int bytes) {
        return bytes>=0 && bytes<=512*1024-(worldWritten-worldRead) && worldEventsWritten-worldEventsRead<16
                && written-read<CAPACITY-2 && copyBudget.test(bytes);
    }
    @Override public void world(WorldSnapshot.Update update) {
        long write=written;
        if(write-read>=CAPACITY || worldEventsWritten-worldEventsRead>=16) {dropped++;return;}
        int slot=(int)write & (CAPACITY-1);
        payloads[slot]=update;kinds[slot]=NativePackets.WORLD_DATA;nanos[slot]=System.nanoTime();
        worldWritten+=update.estimatedBytes();worldEventsWritten++;written=write+1;
    }

    @Override public void event(int kind, long nano, int id, int flag, double x, double y, double z, float yaw, float pitch) {
        long write = written;
        if (write - read >= CAPACITY) { dropped++; return; }
        int slot = (int) write & (CAPACITY - 1);
        kinds[slot] = kind; nanos[slot] = nano; ids[slot] = id; flags[slot] = flag;
        xs[slot] = x; ys[slot] = y; zs[slot] = z; yaws[slot] = yaw; pitches[slot] = pitch;
        written = write + 1; // Release publication, after every primitive field.
    }

    boolean poll(Cursor into) {
        long next = read;
        if (next == written) return false;
        int slot = (int) next & (CAPACITY - 1);
        into.kind = kinds[slot]; into.nano = nanos[slot]; into.id = ids[slot]; into.flags = flags[slot];
        into.x = xs[slot]; into.y = ys[slot]; into.z = zs[slot]; into.yaw = yaws[slot]; into.pitch = pitches[slot];
        into.payload=payloads[slot];payloads[slot]=null;
        if(into.payload!=null){worldRead+=into.payload.estimatedBytes();worldEventsRead++;}
        read = next + 1; // Release slot only after copying all fields.
        return true;
    }

    long dropped() { return dropped; }
    boolean nextIsEncodedChunk() {long next=read;return next!=written && payloads[(int)next&(CAPACITY-1)] instanceof WorldSnapshot.EncodedChunk;}
    int size() { return (int) (written - read); }
    static final class Cursor {
        int kind, id, flags; long nano; double x, y, z; float yaw, pitch; WorldSnapshot.Update payload;
    }
}
