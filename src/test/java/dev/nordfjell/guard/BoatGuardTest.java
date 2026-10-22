package dev.nordfjell.guard;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoatGuardTest {
    @Test void seatedFeetDoNotMakeOrdinarySupportedBoatLookEmbedded() {
        var body=new BoundingBox(-.3,79.5875,-.3,.3,81.3875,.3);
        var occupied=BoatGuard.seatedBody(body,80);
        assertNotNull(occupied);assertEquals(80.01,occupied.getMinY());
        assertEquals(body.getMaxY(),occupied.getMaxY());
        assertFalse(occupied.overlaps(new BoundingBox(-1,79,-1,1,80,1)));
        assertEquals(79.5875,body.getMinY());
    }
    @Test void trimmingSeatedFeetDoesNotRemoveCeilingOrSideCollision() {
        var occupied=BoatGuard.seatedBody(new BoundingBox(-.3,79.5875,-.3,.3,81.3875,.3),80);
        assertTrue(occupied.overlaps(new BoundingBox(-1,81,-1,1,82,1)));
        assertTrue(occupied.overlaps(new BoundingBox(.2,80,-1,1,82,1)));
    }
    @Test void unknownBelowBasePassengerShapeDefersInsteadOfMakingAnEmptyBox() {
        assertNull(BoatGuard.seatedBody(new BoundingBox(0,79,0,1,80,1),80));
        assertNull(BoatGuard.seatedBody(new BoundingBox(0,79,0,1,81,1),Double.NaN));
    }
}
