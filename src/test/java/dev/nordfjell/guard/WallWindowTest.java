package dev.nordfjell.guard;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WallWindowTest {
    private static final UUID WORLD=new UUID(0,1),TARGET=new UUID(0,2);
    private static final List<Geometry.Box> WALL=List.of(new Geometry.Box(1,1,1,2,2,2));
    @Test void needsRepeatedRecentObservations() {
        var window=new WallWindow();
        assertFalse(window.blocked(WORLD,TARGET,WALL,0));
        assertFalse(window.blocked(WORLD,TARGET,WALL,100_000_000));
        assertTrue(window.blocked(WORLD,TARGET,WALL,200_000_000));
    }
    @Test void oldWallCannotConfirmNewObservationAfterLongGap() {
        var window=new WallWindow();window.blocked(WORLD,TARGET,WALL,1);
        assertFalse(window.blocked(WORLD,TARGET,WALL,1_500_000_002L));
        assertFalse(window.blocked(WORLD,TARGET,WALL,10_000_000_000L));
    }
    @Test void boundaryAndBackwardClockAreConservative() {
        var window=new WallWindow();window.blocked(WORLD,TARGET,WALL,100);
        assertTrue(window.blocked(WORLD,TARGET,WALL,100+WallWindow.WINDOW));
        assertFalse(window.blocked(WORLD,TARGET,WALL,99));
    }
    @Test void worldTargetGeometryAndUnknownResetConfirmation() {
        var window=new WallWindow();window.blocked(WORLD,TARGET,WALL,0);
        assertFalse(window.blocked(new UUID(0,3),TARGET,WALL,WallWindow.WINDOW));
        assertFalse(window.blocked(WORLD,new UUID(0,4),WALL,2*WallWindow.WINDOW));
        assertFalse(window.blocked(WORLD,TARGET,List.of(new Geometry.Box(2,1,1,3,2,2)),3*WallWindow.WINDOW));
        window.reset();assertFalse(window.blocked(WORLD,TARGET,WALL,4*WallWindow.WINDOW));
    }
    @Test void ownerExpiryDropsRetainedGeometryEvenWithoutAttacks() {
        var window=new WallWindow();window.blocked(WORLD,TARGET,WALL,0);
        window.expire(1_500_000_001L);
        assertFalse(window.blocked(WORLD,TARGET,WALL,1_500_000_002L));
    }
    @Test void matchingHashesDoNotConfirmDifferentGeometry() {
        double collision=Double.longBitsToDouble(0x3ff0000100000001L);
        var other=List.of(new Geometry.Box(collision,1,1,2,2,2));
        assertEquals(WALL.hashCode(),other.hashCode());assertNotEquals(WALL,other);
        var window=new WallWindow();window.blocked(WORLD,TARGET,WALL,0);
        assertFalse(window.blocked(WORLD,TARGET,other,WallWindow.WINDOW));
    }
    @Test void normalWeaponCooldownDoesNotEraseConfirmedWall() {
        for(long gap:new long[]{100_000_000L,500_000_000L,650_000_000L,1_000_000_000L}) {
            var window=new WallWindow();assertFalse(window.blocked(WORLD,TARGET,WALL,0));
            window.refresh(WORLD,TARGET,WALL,100_000_000L);window.refresh(WORLD,TARGET,WALL,200_000_000L);
            assertTrue(window.blocked(WORLD,TARGET,WALL,gap<200_000_000L?200_000_000L:gap));
        }
    }
    @Test void ownerRefreshCannotRetainGeometryForeverWithoutAttacks() {
        var window=new WallWindow();window.blocked(WORLD,TARGET,WALL,0);
        for(long t=100_000_000;t<=1_600_000_000L;t+=100_000_000)window.refresh(WORLD,TARGET,WALL,t);
        assertFalse(window.present());
    }
}
