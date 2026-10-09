package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Finite SPSC stress regressions, not a proof of all memory-model interleavings. */
class WorldPublicationTest {
    @Test void crossThreadPublicationSurvivesSlotReuseAndReleasesEveryPayload() throws Exception {
        int events = 40_000;
        var q = new PacketInbox(bytes -> true);
        var expected = new WorldSnapshot.Update[events];
        long bytes = 0;
        for (int i = 0; i < events; i += 4) {
            expected[i] = switch ((i / 4) % 3) {
                case 0 -> new WorldSnapshot.Blocks(new int[]{i, -i, i + 1, i + 2});
                case 1 -> new WorldSnapshot.EncodedChunk(i, -i, new byte[]{(byte) i, (byte) (i >>> 8)});
                default -> new WorldSnapshot.Forget(i, -i);
            };
            bytes += expected[i].estimatedBytes();
        }
        var start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2, task -> {
            var thread = new Thread(task, "world-publication-test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<?> producer = workers.submit(() -> {
                await(start);
                for (int i = 0; i < events; i++) {
                    var payload = expected[i];
                    if (payload != null) {
                        while (!q.reserveWorldCopy(payload.estimatedBytes())) spin();
                        q.world(payload);
                    } else {
                        while (q.size() >= PacketInbox.CAPACITY - 2) spin();
                        q.event(NativePackets.INPUT, i, i, ~i, i + .25, -i - .5,
                                i * 2, i + .5f, -i - .25f);
                    }
                    if ((i & 127) == 0) Thread.yield();
                }
            });
            Future<?> consumer = workers.submit(() -> {
                await(start);
                var cursor = new PacketInbox.Cursor();
                for (int i = 0; i < events; i++) {
                    while (q.size() == 0) spin();
                    assertEquals(expected[i] instanceof WorldSnapshot.EncodedChunk, q.nextIsEncodedChunk());
                    assertTrue(q.poll(cursor));
                    if (expected[i] != null) {
                        assertEquals(NativePackets.WORLD_DATA, cursor.kind);
                        assertSame(expected[i], cursor.payload);
                        if (cursor.payload instanceof WorldSnapshot.Blocks blocks) {
                            assertEquals(i, blocks.x(0));
                            assertEquals(-i, blocks.y(0));
                            assertEquals(i + 1, blocks.z(0));
                            assertEquals(i + 2, blocks.stateId(0));
                        } else if (cursor.payload instanceof WorldSnapshot.EncodedChunk chunk) {
                            assertEquals(i, chunk.x());
                            assertEquals(-i, chunk.z());
                            assertArrayEquals(new byte[]{(byte) i, (byte) (i >>> 8)}, chunk.payload());
                        }
                    } else {
                        assertNull(cursor.payload, "primitive slot retained an old world payload");
                        assertEquals(NativePackets.INPUT, cursor.kind);
                        assertEquals(i, cursor.nano);
                        assertEquals(i, cursor.id);
                        assertEquals(~i, cursor.flags);
                        assertEquals(i + .25, cursor.x);
                        assertEquals(-i - .5, cursor.y);
                        assertEquals(i * 2, cursor.z);
                        assertEquals(i + .5f, cursor.yaw);
                        assertEquals(-i - .25f, cursor.pitch);
                    }
                    if ((i & 63) == 0) Thread.yield();
                }
            });
            start.countDown();
            producer.get(15, TimeUnit.SECONDS);
            consumer.get(15, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(0, q.size());
        assertEquals(0, q.dropped());
        assertEquals(bytes, counter(q, "worldWritten"));
        assertEquals(bytes, counter(q, "worldRead"));
        assertEquals(events / 4, counter(q, "worldEventsWritten"));
        assertEquals(events / 4, counter(q, "worldEventsRead"));
        assertTrue(q.reserveWorldCopy(512 * 1024));
        assertFalse(q.nextIsEncodedChunk());
        assertFalse(q.poll(new PacketInbox.Cursor()));
        Field payloads = PacketInbox.class.getDeclaredField("payloads");
        payloads.setAccessible(true);
        for (Object payload : (Object[]) payloads.get(q)) assertNull(payload);
        // The consumer's released ring slot must not mutate an update retained by its caller.
        assertEquals(events - 4, ((WorldSnapshot.Blocks) expected[events - 4]).x(0));
    }

    @Test void byteBudgetIsExactAndReturnedOnlyAfterConsumption() {
        var q = new PacketInbox(bytes -> true);
        var full = new WorldSnapshot.EncodedChunk(0, 0, new byte[512 * 1024 - 48]);
        assertTrue(q.reserveWorldCopy(full.estimatedBytes()));
        q.world(full);
        assertFalse(q.reserveWorldCopy(1));
        assertTrue(q.nextIsEncodedChunk());
        var cursor = new PacketInbox.Cursor();
        assertTrue(q.poll(cursor));
        assertSame(full, cursor.payload);
        assertTrue(q.reserveWorldCopy(512 * 1024));
        assertFalse(q.reserveWorldCopy(512 * 1024 + 1));
        assertFalse(q.reserveWorldCopy(-1));
    }

    @Test void interleavedPrimitiveEventsDoNotConsumeOrReleaseWorldEventAllowance() {
        var calls = new AtomicInteger();
        var q = new PacketInbox(bytes -> { calls.incrementAndGet(); return true; });
        for (int i = 0; i < 16; i++) {
            assertTrue(q.reserveWorldCopy(24));
            q.world(new WorldSnapshot.Forget(i, -i));
            q.event(NativePackets.INPUT, i, i, i, 0, 0, 0, 0, 0);
        }
        assertEquals(16, calls.get());
        assertFalse(q.reserveWorldCopy(24));
        assertEquals(16, calls.get(), "local limit must reject before calling the shared budget");
        var cursor = new PacketInbox.Cursor();
        assertTrue(q.poll(cursor));
        assertTrue(q.reserveWorldCopy(24));
        q.world(new WorldSnapshot.Retain(99, 99));
        assertTrue(q.poll(cursor));
        assertNull(cursor.payload);
        assertFalse(q.reserveWorldCopy(24), "reading INPUT must not release a world event");
        assertEquals(17, calls.get());
        int worlds = 0;
        while (q.poll(cursor)) if (cursor.payload != null) worlds++;
        assertEquals(16, worlds);
        assertTrue(q.reserveWorldCopy(512 * 1024));
        assertEquals(0, q.dropped());
    }

    @Test void deniedSharedBudgetAndQueueHeadroomDoNotPublishOrChargePayloads() throws Exception {
        var calls = new AtomicInteger();
        var denied = new PacketInbox(bytes -> { calls.incrementAndGet(); return false; });
        assertFalse(denied.reserveWorldCopy(48));
        assertEquals(1, calls.get());
        assertEquals(0, denied.size());
        assertEquals(0, counter(denied, "worldWritten"));
        assertEquals(0, counter(denied, "worldEventsWritten"));
        var q = new PacketInbox(bytes -> { calls.incrementAndGet(); return true; });
        for (int i = 0; i < PacketInbox.CAPACITY - 2; i++)
            q.event(NativePackets.INPUT, i, i, 0, 0, 0, 0, 0, 0);
        assertFalse(q.reserveWorldCopy(48));
        assertEquals(1, calls.get());
        assertTrue(q.poll(new PacketInbox.Cursor()));
        assertTrue(q.reserveWorldCopy(48));
        assertEquals(2, calls.get());
        assertEquals(0, counter(q, "worldWritten"));
    }

    private static long counter(PacketInbox inbox, String name) throws Exception {
        Field field = PacketInbox.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getLong(inbox);
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test interrupted", interrupted);
        }
    }

    private static void spin() {
        if (Thread.currentThread().isInterrupted()) throw new AssertionError("test interrupted");
        Thread.onSpinWait();
    }
}
