package dev.nordfjell.guard;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionHistoryTest {
    private static final UUID WORLD = new UUID(0, 1);
    private static final UUID OTHER_WORLD = new UUID(0, 2);
    private static final Geometry.Point EYE = new Geometry.Point(.5, 65.62, .5);
    private static final Geometry.Box BOX = new Geometry.Box(.2, 64, .2, .8, 65.8, .8);

    private static ActionGuard.View view(long time) {
        return new ActionGuard.View(WORLD, time, EYE, BOX);
    }

    // Exercise the production predicate without constructing a plugin or a Bukkit server.
    private static boolean fresh(ActionGuard.View view, UUID world, long now, long age) throws Exception {
        Method method = ActionGuard.class.getDeclaredMethod("fresh", ActionGuard.View.class,
                UUID.class, long.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, view, world, now, age);
    }

    @Test void zeroAgeOnlyAcceptsSameTimestamp() throws Exception {
        assertTrue(fresh(view(100), WORLD, 100, 0));
        assertFalse(fresh(view(100), WORLD, 101, 0));
    }

    @Test void exactAgeBoundaryRemainsUsable() throws Exception {
        assertTrue(fresh(view(1_000_000_000L), WORLD, 1_200_000_000L, 200_000_000L));
    }

    @Test void oneNanosecondPastAgeBoundaryDefers() throws Exception {
        assertFalse(fresh(view(1_000_000_000L), WORLD, 1_200_000_001L, 200_000_000L));
    }

    @Test void futureSnapshotCannotSupplyHistory() throws Exception {
        assertFalse(fresh(view(101), WORLD, 100, 200_000_000L));
    }

    @Test void anotherWorldCannotSupplyHistory() throws Exception {
        assertFalse(fresh(view(100), OTHER_WORLD, 100, 200_000_000L));
    }

    @Test void absentSnapshotDefers() throws Exception {
        assertFalse(fresh(null, WORLD, 100, 200_000_000L));
    }

    @Test void freshnessDoesNotChangeSnapshotOrGeometry() throws Exception {
        var snapshot = view(100);
        assertTrue(fresh(snapshot, WORLD, 110, 20));
        assertFalse(fresh(snapshot, OTHER_WORLD, 110, 20));
        assertEquals(new ActionGuard.View(WORLD, 100, EYE, BOX), snapshot);
        assertSame(EYE, snapshot.eye());
        assertSame(BOX, snapshot.box());
    }

    @Test void publishingNextPairDoesNotMutateAnExistingReadersPair() {
        var older = view(100);
        var current = view(200);
        var original = new ActionGuard.History(current, older, 7);
        var published = new AtomicReference<>(original);
        var reader = published.get();
        var next = new ActionGuard.View(WORLD, 300, new Geometry.Point(1.5, 65.62, .5),
                BOX.expand(.1));

        published.set(new ActionGuard.History(next, original.current(), 7));

        assertSame(original, reader);
        assertSame(current, reader.current());
        assertSame(older, reader.previous());
        assertEquals(7, reader.sequence());
        assertSame(next, published.get().current());
        assertSame(current, published.get().previous());
        assertEquals(new Geometry.Box(.2, 64, .2, .8, 65.8, .8), reader.current().box());
    }
}
