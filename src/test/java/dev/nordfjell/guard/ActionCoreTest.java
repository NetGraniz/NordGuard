package dev.nordfjell.guard;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionCoreTest {
    private static final Geometry.Box CUBE=new Geometry.Box(0,0,0,1,1,1);
    private static Geometry.Point p(double x,double y,double z) { return new Geometry.Point(x,y,z); }
    @Test void distanceUsesNearestSurfaceNotCenter() { assertEquals(2,CUBE.distance(p(3,.5,.5)));assertEquals(0,CUBE.distance(p(.5,.5,.5))); }
    @Test void crossingWallInBothDirections() { assertTrue(CUBE.crosses(p(-1,.5,.5),p(2,.5,.5)));assertTrue(CUBE.crosses(p(2,.5,.5),p(-1,.5,.5))); }
    @Test void grazingFaceAndTouchingEndpointAllowed() { assertFalse(CUBE.crosses(p(-1,1,.5),p(2,1,.5)));assertFalse(CUBE.crosses(p(-1,.5,.5),p(0,.5,.5))); }
    @Test void parallelMissAndDiagonalCornerAllowed() { assertFalse(CUBE.crosses(p(-1,2,.5),p(2,2,.5)));assertFalse(CUBE.crosses(p(-1,1,.5),p(1,3,.5))); }
    @Test void zeroLengthOutsideDoesNotCross() { assertFalse(CUBE.crosses(p(2,2,2),p(2,2,2))); }
    @Test void invalidGeometryRejected() { assertThrows(IllegalArgumentException.class,()->new Geometry.Box(2,0,0,1,1,1));assertThrows(IllegalArgumentException.class,()->new Geometry.Box(0,0,0,Double.NaN,1,1)); }
    @Test void visibleCornerAvoidsWallViolation() {
        var scan=new SolidProbe.Scan(true,java.util.List.of(CUBE));
        assertFalse(scan.obscured(p(-2,.5,.5),new Geometry.Box(2,0,0,3,3,1)));
        assertTrue(scan.obscured(p(-2,.5,.5),new Geometry.Box(2,.1,.1,3,.9,.9)));
    }
    @Test void tokenBurstAtZeroClockAndRefill() {
        var b=new ActionBudget();for(int i=0;i<20;i++) assertTrue(b.allow(0,20));
        assertFalse(b.allow(0,20));assertTrue(b.allow(50_000_000,20));assertFalse(b.allow(50_000_000,20));
    }
    @Test void tokenCreditCannotAccumulateBeyondBurst() {
        var b=new ActionBudget();b.allow(1,5);
        for(int i=0;i<5;i++) assertTrue(b.allow(100_000_000_000L,5));
        assertFalse(b.allow(100_000_000_000L,5));
    }
    @Test void backwardClockDoesNotMintCredit() {
        var b=new ActionBudget();for(int i=0;i<5;i++) b.allow(1_000_000_000L,5);
        assertFalse(b.allow(0,5));assertFalse(b.allow(1_000_000_000L,5));
    }
    @Test void nativeEarlyStopAndTwoTickGrace() { var p=new MiningProgress(.1,false);for(int i=0;i<4;i++) p.advance(.1);assertFalse(p.premature()); }
    @Test void nativeToolUpgradeAppliesToWholeElapsedWindow() {
        var p=new MiningProgress(.001,false);for(int i=0;i<10;i++) p.advance(.001);
        assertTrue(p.premature());assertFalse(p.premature(.1));
    }
    @Test void immediatelyBreakingSlowBlockIsPremature() { assertTrue(new MiningProgress(.01,false).premature()); }
    @Test void lagCompensatedMiningUsesElapsedTimeNotOnlySamples() {
        var p=new MiningProgress(.05,false,0);
        for(int i=0;i<5;i++) p.advance(.05);
        assertTrue(p.premature());assertFalse(p.premature(.05,650_000_000));
    }
    @Test void instantPermissionAndFastToolChangeAllowed() { assertFalse(new MiningProgress(.001,true).premature());var p=new MiningProgress(.01,false);p.advance(1);assertFalse(p.premature()); }
    @Test void miningProgressRemainsBoundedAndIgnoresInvalidSamples() { var p=new MiningProgress(.01,false);p.advance(Double.NaN);p.advance(-1);assertTrue(p.premature());for(int i=0;i<10000;i++) p.advance(1);assertFalse(p.premature()); }
    @Test void globalSpatialCapAndWindowReset() {
        var b=new SpatialBudget();assertTrue(b.acquire(0,600,20000));assertFalse(b.acquire(0,401,20000));
        assertTrue(b.acquire(0,400,20000));assertFalse(b.acquire(0,1,20000));assertTrue(b.acquire(50_000_000,1000,20000));
    }
    @Test void spatialBudgetCannotBeOversizedOrNegative() { var b=new SpatialBudget();assertFalse(b.acquire(0,1001,20000));assertFalse(b.acquire(0,-1,20000)); }
    @Test void staleConcurrentWindowCannotReopenBudget() {
        var b=new SpatialBudget();assertTrue(b.acquire(100_000_000,1000,20000));
        assertFalse(b.acquire(50_000_000,1000,20000));assertFalse(b.acquire(100_000_000,1,20000));
    }
    @Test void concurrentRegionsShareSingleCap() throws Exception {
        var b=new SpatialBudget();var accepted=new java.util.concurrent.atomic.AtomicInteger();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(8)) {
            for(int i=0;i<600;i++) pool.submit(()->{for(int j=0;j<10;j++) if(b.acquire(100_000_000,1,20000)) accepted.incrementAndGet();});
        }
        assertEquals(1000,accepted.get());
    }
    @Test void actionsValidateAndMissingModesObserve() {
        var c=new MemoryConfiguration();c.set("schema-version",1);var policy=Policy.read(c);
        assertEquals(20000,policy.actions().spatialBlocksPerSecond());
        for(Check check:Check.values()) assertEquals(Policy.Mode.OBSERVE,policy.modes().get(check));
        for(String key:new String[]{"history-ms","max-blocks-per-scan","spatial-checks-per-tick","attacks-per-second","spatial-blocks-per-second"}) {
            c.set("actions."+key,Integer.MAX_VALUE);assertThrows(IllegalArgumentException.class,()->Policy.read(c));c.set("actions."+key,null);
        }
        c.set("actions.reach-margin",Double.NaN);assertThrows(IllegalArgumentException.class,()->Policy.read(c));
    }
}
