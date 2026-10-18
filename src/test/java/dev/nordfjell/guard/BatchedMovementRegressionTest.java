package dev.nordfjell.guard;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchedMovementRegressionTest {
    private static MovementModel.Frame frame(double x,double y,boolean ground,double use) {
        return new MovementModel.Frame(x,y,0,ground,false,false,.286,.42,.6,.08,false,false,false,use);
    }
    private static EnumSet<Check> fall(int[] pattern) {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        model.accept(frame(0,300,true,1),policy);model.accept(frame(0,300,true,1),policy);
        var flags=EnumSet.noneOf(Check.class);double y=300,velocity=0;
        for(int owner=0;owner<90;owner++) {
            int steps=pattern[owner%pattern.length];
            if(steps==0) {model.transportIdleTick();continue;}
            for(int i=0;i<steps;i++){y+=velocity;velocity=(velocity-.08)*.98;}
            flags.addAll(model.accept(frame(1,y,false,1),policy).flags());
        }
        return flags;
    }
    @Test void ordinaryGravityRemainsAllowed() {assertFalse(fall(new int[]{1}).contains(Check.FLIGHT));}
    @Test void twoOneSilentFallDoesNotFlagFlight() {assertFalse(fall(new int[]{2,1,0}).contains(Check.FLIGHT));}
    @Test void silentTwoOneFallDoesNotFlagFlight() {assertFalse(fall(new int[]{0,2,1}).contains(Check.FLIGHT));}
    @Test void tripleSilentSilentFallDoesNotFlagFlight() {assertFalse(fall(new int[]{3,0,0}).contains(Check.FLIGHT));}
    @Test void slowingDescentAndHoverStillFlagAfterNaturalFall() {
        for(double descent:new double[]{0,.125,.5}) {
            var model=new MovementModel();var policy=MovementModelTest.policy();double y=300,v=0;
            model.accept(frame(0,y,true,1),policy);
            for(int i=0;i<30;i++){y+=v;v=(v-.08)*.98;model.accept(frame(1,y,false,1),policy);}
            boolean flag=false;
            for(int i=0;i<100;i++){y-=descent;flag|=model.accept(frame(1,y,false,1),policy).flags().contains(Check.FLIGHT);}
            assertTrue(flag,"descent="+descent);
        }
    }
    @Test void fastDescentCannotBuyUnboundedSlowfall() {
        var model=new MovementModel();var policy=MovementModelTest.policy();double y=300;
        model.accept(frame(0,y,true,1),policy);
        for(int i=0;i<30;i++){y-=4;model.accept(frame(1,y,false,1),policy);}
        y-=10000;model.accept(frame(1,y,false,1),policy);
        boolean flag=false;
        for(int i=0;i<60;i++){y-=.125;flag|=model.accept(frame(1,y,false,1),policy).flags().contains(Check.FLIGHT);}
        assertTrue(flag);
    }
    private static EnumSet<Check> item(double step,boolean batched) {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        model.accept(frame(0,64,true,.2),policy);
        var flags=EnumSet.noneOf(Check.class);double x=0;int idles=0;
        for(int t=1;t<=200;t++) {
            if(!batched || t%2==1) {x+=step*(batched?2:1);flags.addAll(model.accept(frame(x,64,true,.2),policy).flags());}
            else if(++idles%5==0) flags.addAll(model.accept(frame(x,64,true,.2),policy).flags());
            else model.stationaryTick();
        }
        return flags;
    }
    @Test void ordinaryItemUseStillAllowed() {assertFalse(item(.0572,false).contains(Check.NOSLOW));}
    @Test void twoTickItemBatchesStillAllowed() {assertFalse(item(.0572,true).contains(Check.NOSLOW));}
    @Test void realNoSlowStillFlagsSingleAndBatched() {
        assertTrue(item(.25,false).contains(Check.NOSLOW));assertTrue(item(.25,true).contains(Check.NOSLOW));
    }
    @Test void idleCreditIsBoundedAndResetClearsIt() {
        var model=new MovementModel();var policy=MovementModelTest.policy();double x=0;
        for(int i=0;i<10;i++)model.accept(frame(0,64,true,.2),policy);
        for(int i=0;i<10000;i++)model.stationaryTick();
        boolean flag=false;
        for(int i=0;i<80;i++){x+=.25;flag|=model.accept(frame(x,64,true,.2),policy).flags().contains(Check.NOSLOW);}
        assertTrue(flag);model.reset();
        assertFalse(model.accept(frame(0,64,true,.2),policy).clean());
        var fresh=new MovementModel();fresh.accept(frame(0,64,true,.2),policy);
        for(int i=1;i<80;i++) {
            var next=frame(i*.0572,64,true,.2);
            assertEquals(fresh.accept(next,policy),model.accept(next,policy));
        }
    }
    @Test void outstandingSlowDebtCannotRefreshCleanAnchor() {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        for(int i=0;i<10;i++)model.accept(frame(0,64,true,.2),policy);
        double x=0;for(int i=0;i<20;i++){x+=.25;model.accept(frame(x,64,true,.2),policy);}
        assertFalse(model.accept(frame(x+.25,64,true,.2),policy).clean());
    }
    @Test void endingUseDoesNotMintFreshBurstOrRefreshUnpaidAnchor() {
        var model=new MovementModel();var policy=MovementModelTest.policy();double x=0;boolean detected=false;
        for(int i=0;i<10;i++)model.accept(frame(0,64,true,.2),policy);
        for(int cycle=0;cycle<100;cycle++) {
            for(int i=0;i<5;i++) {x+=.15;detected|=model.accept(frame(x,64,true,.2),policy).flags().contains(Check.NOSLOW);}
            x+=.15;
            var released=model.accept(frame(x,64,true,1),policy);
            // The initial grounded warmup earned one legitimate bounded reserve.
            // Later restarts must not replenish that already consumed reserve.
            if(cycle>0)assertFalse(released.clean());
            for(int i=0;i<10;i++) {x+=.15;model.accept(frame(x,64,true,1),policy);}
        }
        assertTrue(detected);
    }
    @Test void changingJumpAndGravityCannotRestartFlightClock() {
        var model=new MovementModel();var policy=MovementModelTest.policy();double y=300;boolean detected=false;
        model.accept(frame(0,y,true,1),policy);
        for(int i=0;i<180;i++) {
            y-=.125;double jump=i%20<10?.42:.43,gravity=i%20<10?.08:.079;
            var next=new MovementModel.Frame(1,y,0,false,false,false,.286,jump,.6,gravity);
            detected|=model.accept(next,policy).flags().contains(Check.FLIGHT);
        }
        assertTrue(detected);
    }
    @Test void fullAndShortIdleHaveSameBoundedUseCredit() {
        var shortIdle=new MovementModel();var longIdle=new MovementModel();var policy=MovementModelTest.policy();
        for(int i=0;i<10;i++){shortIdle.accept(frame(0,64,true,.2),policy);longIdle.accept(frame(0,64,true,.2),policy);}
        for(int i=0;i<5;i++)shortIdle.stationaryTick();
        for(int i=0;i<10000;i++)longIdle.stationaryTick();
        for(int i=1;i<60;i++)assertEquals(shortIdle.accept(frame(i*.25,64,true,.2),policy),longIdle.accept(frame(i*.25,64,true,.2),policy));
    }
    @Test void switchingToSmallerSpeedClampsUseCredit() throws Exception {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        var fast=new MovementModel.Frame(0,64,0,true,false,false,2,.42,.6,.08,false,false,false,.2);
        for(int i=0;i<10;i++)model.accept(fast,policy);
        for(int i=0;i<100;i++)model.stationaryTick();
        model.accept(frame(.0572,64,true,.2),policy);
        var credit=MovementModel.class.getDeclaredField("slowCredit");credit.setAccessible(true);
        assertTrue(credit.getDouble(model)<=.286*.2*policy.burstTicks());
        boolean detected=false;for(int i=1;i<60;i++)detected|=model.accept(frame(i*.25,64,true,.2),policy).flags().contains(Check.NOSLOW);
        assertTrue(detected);
    }
    @Test void seededServerDestinationMeasuresLateMoveBeforeTrustingNewAnchor() {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        model.reset();model.accept(frame(0,64,true,1),policy);
        assertFalse(model.accept(frame(1.5,64,true,1),policy).clean());
        assertFalse(model.accept(frame(1.5,64,true,1),policy).clean());
        for(int i=0;i<5;i++)model.stationaryTick();
        assertTrue(model.accept(frame(1.5,64,true,1),policy).clean());
    }
    @Test void disabledNoSlowCannotMakeOtherwiseLegalMovementDirty() {
        var base=MovementModelTest.policy();var modes=new java.util.EnumMap<Check,Policy.Mode>(Check.class);
        modes.putAll(base.modes());modes.put(Check.NOSLOW,Policy.Mode.OFF);
        var policy=new Policy(modes,base.buffer(),base.horizontalMargin(),base.verticalMargin(),base.burstTicks(),
                base.joinGrace(),base.transitionGrace(),base.maxGapNanos(),base.alertNanos(),base.console());
        var model=new MovementModel();model.accept(frame(0,64,true,.2),policy);
        for(int i=1;i<=60;i++)assertTrue(model.accept(frame(i*.25,64,true,.2),policy).clean());
    }
}
