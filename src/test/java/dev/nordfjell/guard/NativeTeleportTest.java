package dev.nordfjell.guard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeTeleportTest {
    @Test void serverSequenceIncrementAndWrap() {
        assertEquals(1, NativeTeleport.next(0));
        assertEquals(Integer.MAX_VALUE - 1, NativeTeleport.next(Integer.MAX_VALUE - 2));
        assertEquals(0, NativeTeleport.next(Integer.MAX_VALUE - 1));
    }
}
