package dev.nordfjell.guard;

import java.util.EnumSet;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LowTpsMovementTest {
    private static MovementModel.Frame frame(double x,double y,boolean ground,boolean wall,double use) {
        return new MovementModel.Frame(x,y,0,ground,wall,false,.286,.42,.6,.08,false,false,false,use);
    }
    @Test void oldPerOwnerAllowanceReproducesOrdinaryLowTpsSpeedFalsePositive() {
        var model=new MovementModel();var policy=MovementModelTest.policy();boolean flagged=false;
        model.accept(frame(0,80,true,false,1),policy);
        for(int i=1;i<=100;i++)flagged|=model.accept(frame(i*.286*2.5,80,true,false,1),policy).flags().contains(Check.SPEED);
        assertTrue(flagged,"Control must reproduce the old 8 TPS failure");
    }
    @Test void ordinaryWalkAndSprintAtTwentyFifteenTenAndEightTpsDoNotFlag() {
        for(double ticks:new double[]{1,20.0/15,2,2.5,3,4}) {
            var model=new MovementModel();var policy=MovementModelTest.policy();
            model.accept(frame(0,80,true,false,1),policy);
            for(int i=1;i<=500;i++)assertTrue(model.accept(frame(i*.286*ticks,80,true,false,1),policy,80,ticks).flags().isEmpty(),"elapsed="+ticks);
        }
    }
    @Test void sustainedSpeedHackStillFlagsAtTheSameLowTps() {
        for(double ticks:new double[]{1,20.0/15,2,2.5,3,4}) {
            var model=new MovementModel();var policy=MovementModelTest.policy();boolean flagged=false;
            model.accept(frame(0,80,true,false,1),policy);
            for(int i=1;i<=100;i++)flagged|=model.accept(frame(i*.286*2.5*ticks,80,true,false,1),policy,80,ticks).flags().contains(Check.SPEED);
            assertTrue(flagged,"elapsed="+ticks);
        }
    }
    @Test void caughtUpOwnerCallsCannotEachMintAnotherFiftyMilliseconds() {
        var model=new MovementModel();var policy=MovementModelTest.policy();boolean flagged=false;
        model.accept(frame(0,80,true,false,1),policy);
        for(int i=1;i<=100;i++)flagged|=model.accept(frame(i*.286,80,true,false,1),policy,80,.1).flags().contains(Check.SPEED);
        assertTrue(flagged);
    }
    @Test void stationaryLowTpsCreditRemainsBurstBounded() {
        var model=new MovementModel();var policy=MovementModelTest.policy();boolean flagged=false;
        model.accept(frame(0,80,true,false,1),policy);
        for(int i=0;i<10000;i++)model.stationaryTick(4);
        for(int i=1;i<=100;i++)flagged|=model.accept(frame(i*3,80,true,false,1),policy,80,2.5).flags().contains(Check.SPEED);
        assertTrue(flagged);
    }
    @Test void ordinaryAndExcessiveShieldMovementKeepSeparateLowTpsBudgets() {
        for(double multiplier:new double[]{.2,1}) {
            var model=new MovementModel();var policy=MovementModelTest.policy();boolean flagged=false;
            model.accept(frame(0,80,true,false,.2),policy);
            for(int i=1;i<=150;i++)flagged|=model.accept(frame(i*.286*multiplier*2.5,80,true,false,.2),policy,80,2.5).flags().contains(Check.NOSLOW);
            assertEquals(multiplier==1,flagged);
        }
    }
    @Test void ordinaryJumpCyclesAlongWallAtLowTpsDoNotFlag() {
        for(int[] pattern:new int[][]{{1},{2},{2,3},{3},{4}}) {
            var model=new MovementModel();var policy=MovementModelTest.policy();
            double x=0,y=80,vy=.42;int nativeTick=0;
            model.accept(frame(x,y,true,true,1),policy,80);
            var flags=EnumSet.noneOf(Check.class);
            for(int sample=0;sample<300;sample++) {
                int steps=pattern[sample%pattern.length];
                for(int i=0;i<steps;i++) {
                    x+=.286;y+=vy;vy=(vy-.08)*.98;
                    if(y<=80){y=80;vy=.42;}
                    nativeTick++;
                }
                flags.addAll(model.accept(frame(x,y,y==80,true,1),policy,80,steps).flags());
            }
            assertTrue(flags.isEmpty(),"native ticks="+nativeTick+" flags="+flags);
        }
    }
    @Test void ordinaryGroundedStairAscentCanContainMultipleNativeSteps() {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        model.accept(frame(0,80,true,true,1),policy,80);
        for(int i=1;i<=100;i++)assertTrue(model.accept(frame(i*.286*2.5,80+i*1.2,true,true,1),policy,80+i*1.2,2.5).flags().isEmpty());
    }
    @Test void hoverAndWallClimbingAreNotDisabledByLowTps() {
        for(boolean spider:new boolean[]{false,true}) {
            var model=new MovementModel();var policy=MovementModelTest.policy();var flags=EnumSet.noneOf(Check.class);
            for(int i=0;i<150;i++)flags.addAll(model.accept(frame(0,100+(spider?i*.5:0),false,spider,1),policy,Double.NaN,2.5).flags());
            assertTrue(flags.contains(spider?Check.SPIDER:Check.FLIGHT),flags.toString());
        }
    }
    @Test void smallHealthyTickJitterDoesNotDoubleStepAllowance() {
        var model=new MovementModel();var policy=MovementModelTest.policy();boolean flagged=false;
        model.accept(frame(0,80,true,false,1),policy);
        for(int i=1;i<30;i++)flagged|=model.accept(frame(0,80+i,true,false,1),policy,80+i,1.02).flags().contains(Check.HIGHJUMP);
        assertTrue(flagged);
    }
    @Test void monotonicIntervalIsBoundedAndNeverUsesPacketCount() {
        var policy=MovementModelTest.policy();
        assertEquals(2.5,MovementTiming.ticks(125_000_000,policy));
        assertEquals(.1,MovementTiming.ticks(5_000_000,policy));
        assertFalse(MovementTiming.uncertain(200_000_000,policy));
        for(long gap:new long[]{0,-1,200_000_001,Long.MAX_VALUE}) {
            assertTrue(MovementTiming.uncertain(gap,policy));
            assertThrows(IllegalArgumentException.class,()->MovementTiming.ticks(gap,policy));
        }
    }
    @Test void configDefaultsPreserveModesAndRejectInvalidTimingLimits() {
        var config=new MemoryConfiguration();config.set("schema-version",1);config.set("checks.speed","CORRECT");
        assertEquals(4,Policy.read(config).movement().maxElapsedTicks());
        assertEquals(Policy.Mode.CORRECT,Policy.read(config).modes().get(Check.SPEED));
        for(Object value:new Object[]{1,7,2.5,Double.NaN,"4"}) {
            config.set("movement.max-elapsed-ticks",value);
            assertThrows(IllegalArgumentException.class,()->Policy.read(config));
        }
    }
}
