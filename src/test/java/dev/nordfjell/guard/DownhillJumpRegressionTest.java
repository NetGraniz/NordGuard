package dev.nordfjell.guard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DownhillJumpRegressionTest {
    @Test void oneDelayedBurstFollowedByLegalMovementDoesNotKeepAddingSpeedEvidence() {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        model.accept(frame(0,160,true),policy,160);
        for(int tick=0;tick<14;tick++)model.transportIdleTick();
        double x=4.16;
        assertFalse(model.accept(frame(x,160,true),policy,160).flags().contains(Check.SPEED));
        for(int tick=0;tick<80;tick++) {
            x+=.27;
            assertFalse(model.accept(frame(x,160,true),policy,160).flags().contains(Check.SPEED));
        }
    }
    @Test void repeatedExcessBurstsStillAccumulateSpeedEvidence() {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        model.accept(frame(0,160,true),policy,160);boolean detected=false;double x=0;
        for(int tick=0;tick<100;tick++) {
            if(tick%3==0) {x+=3;detected|=model.accept(frame(x,160,true),policy,160).flags().contains(Check.SPEED);}
            else model.transportIdleTick();
        }
        assertTrue(detected);
    }
    @Test void netDownLandingBatchStillRequiresKnownNearbyFloor() {
        assertTrue(netDownBatches(Double.NaN,1).contains(Check.FLIGHT));
        assertTrue(netDownBatches(157,1).contains(Check.FLIGHT));
        assertFalse(netDownBatches(160,1).contains(Check.FLIGHT));
    }
    @Test void inferredNetDownLandingDoesNotForgiveExcessSpeed() {
        assertTrue(netDownBatches(160,3).contains(Check.SPEED));
    }
    @Test void hoverNearFloorStillFlagsAfterNetDownLanding() {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        model.accept(frame(0,160,true),policy,160);
        for(int cycle=0;cycle<6;cycle++)for(double height:new double[]{.42,1.16610928,1.17675928,.49520091})
            model.accept(frame(1,160+height,false),policy,160);
        boolean detected=false;
        for(int tick=0;tick<100;tick++)detected|=model.accept(frame(1,160.42,false),policy,160).flags().contains(Check.FLIGHT);
        assertTrue(detected);
    }
    @Test void netDownLandingDoesNotEraseFallDistance() {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        model.accept(frame(0,180,true),policy);
        for(int tick=0;tick<14;tick++)model.accept(frame(0,179-tick,false),policy);
        model.accept(frame(0,160.49520091,false),policy,160);
        model.accept(frame(.4,160.42,false),policy,160);
        assertEquals(20,model.accept(frame(.7,160,true),policy,160).landingDistance(),1E-9);
    }
    private static java.util.EnumSet<Check> netDownBatches(double support,double horizontal) {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        var flags=java.util.EnumSet.noneOf(Check.class);double x=0;
        model.accept(frame(0,160,true),policy,160);
        for(int cycle=0;cycle<50;cycle++)for(double height:new double[]{.42,1.16610928,1.17675928,.49520091}) {
            x+=1.08*horizontal;
            flags.addAll(model.accept(frame(x,160+height,false),policy,support).flags());
            model.transportIdleTick();model.transportIdleTick();
        }
        return flags;
    }
    @Test void repeatedSprintJumpsAcrossLowerFloors() {
        downhill(false);
    }
    @Test void skippedGroundSnapshotsStillUseElapsedOwnerTimeForNextJump() {
        downhill(true);
    }
    private static void downhill(boolean skipGround) {
        for(int batch=1;batch<=3;batch++)for(int phase=0;phase<32;phase++) {
            var model=new MovementModel();var policy=MovementModelTest.policy();
            double x=0,y=160,velocity=0,floor=160;boolean grounded=true;int cooldown=0;
            model.accept(frame(x,y,true),policy,floor);
            var history=new java.util.ArrayDeque<String>();
            for(int tick=0;tick<600;tick++) {
                double previousFloor=floor;
                floor=x+phase*.45>=120?159:160;
                if(grounded && floor<previousFloor)grounded=false;
                if(grounded && cooldown==0) {velocity=.42;grounded=false;cooldown=10;}
                if(cooldown>0)cooldown--;
                x+=.45;y+=velocity;
                if(y<=floor){y=floor;velocity=0;grounded=true;}
                else velocity=(velocity-.08)*.98;
                if(tick%batch!=batch-1 || skipGround && grounded){model.transportIdleTick();continue;}
                double support=y>=floor && y-floor<=1?floor:Double.NaN;
                var result=model.accept(frame(x,y,grounded || y-floor<=.06),policy,support);
                history.add(tick+":y="+y+", floor="+support+", ground="+(grounded || y-floor<=.06));
                if(history.size()>18)history.remove();
                assertFalse(result.flags().contains(Check.FLIGHT),"batch="+batch+", phase="+phase+", "+history);
            }
        }
    }
    private static MovementModel.Frame frame(double x,double y,boolean ground) {
        return new MovementModel.Frame(x,y,0,ground,false,false,.286,.42,.6,.08);
    }
}
