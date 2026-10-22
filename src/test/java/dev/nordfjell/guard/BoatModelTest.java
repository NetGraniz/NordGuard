package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BoatModelTest {
    private static final BoatLimits LIMITS=BoatLimits.defaults();
    private static final class Trace {
        final BoatModel model=new BoatModel();long time=1_000_000_000L;
        BoatModel.Result at(double x,double y,boolean supported,boolean water,boolean bubble,boolean exempt,boolean fresh,double gravity) {
            time+=50_000_000L;
            return model.accept(new BoatModel.Frame(time,x,y,0,supported,water,bubble,exempt,fresh,gravity),LIMITS);
        }
        BoatModel.Result air(double y) {return at(0,y,false,false,false,false,true,.04);}
        BoatModel.Result ground(double x) {return at(x,80,true,false,false,false,true,.04);}
    }
    @Test void supportedMovementDoesNotCreateFlightEvidence() {
        var trace=new Trace();assertFalse(trace.ground(0).clean());
        for(int i=1;i<200;i++) {
            var result=trace.ground(i*.4);assertFalse(result.violation());assertTrue(result.clean());
        }
    }
    @Test void ordinaryWaterMovementDoesNotCreateFlightEvidence() {
        var trace=new Trace();
        for(int i=0;i<200;i++) assertFalse(trace.at(i*.4,80+Math.sin(i*.2)*.1,false,true,false,false,true,.04).violation());
    }
    @Test void nativeDryFallHasNoVerticalDragAndDoesNotFlag() {
        var trace=new Trace();trace.ground(0);double y=80,velocity=0;
        for(int i=0;i<80;i++) {velocity-=.04;y+=velocity;assertFalse(trace.air(y).violation());}
    }
    @Test void twoOneZeroCoalescedDryFallDoesNotUseBatchDyAsVelocity() {
        for(int phase=0;phase<3;phase++) {
            var trace=new Trace();trace.ground(0);double y=80,velocity=0;
            int[] steps={2,1,0};
            for(int tick=0;tick<90;tick++) {
                for(int k=0;k<steps[(tick+phase)%3];k++) {velocity-=.04;y+=velocity;}
                assertFalse(trace.air(y).violation());
            }
        }
    }
    @Test void unsupportedSustainedRiseAccumulatesEvidence() {
        var trace=new Trace();trace.ground(0);boolean flagged=false;
        for(int i=1;i<=60;i++)flagged|=trace.air(80+i*.3).violation();
        assertTrue(flagged);
    }
    @Test void receivedStationaryHoverAccumulatesEvidence() {
        var trace=new Trace();trace.ground(0);boolean flagged=false;
        for(int i=0;i<90;i++)flagged|=trace.air(82).violation();
        assertTrue(flagged);
    }
    @Test void ConstantSlowDescentCannotReplaceTheBallisticOrigin() {
        var trace=new Trace();trace.ground(0);boolean flagged=false;
        for(int i=1;i<=120;i++)flagged|=trace.air(80-i*.04).violation();
        assertTrue(flagged);
    }
    @Test void unchangedTransportPauseIsNotHoverAndCannotMintNewGrace() {
        var trace=new Trace();trace.ground(0);trace.air(79.96);
        for(int i=0;i<500;i++)assertFalse(trace.at(0,79.96,false,false,false,false,false,.04).violation());
        boolean flagged=false;
        for(int i=0;i<90;i++)flagged|=trace.air(79.96).violation();
        assertTrue(flagged);
    }
    @Test void ownerPositionChangesRemainEvidenceEvenWithoutFreshTransportMarker() {
        var trace=new Trace();trace.ground(0);boolean flagged=false;
        for(int i=1;i<=60;i++)flagged|=trace.at(0,80+i*.3,false,false,false,false,false,.04).violation();
        assertTrue(flagged);
    }
    @Test void actualBubbleLaunchWithPlayerPassengerIsAllowedThroughLanding() {
        var trace=new Trace();trace.at(0,80,false,true,true,false,true,.04);
        double y=80,velocity=2.7;
        for(int i=0;i<160;i++) {
            y+=velocity;velocity-=.04;
            if(y<=80) {assertFalse(trace.ground(0).violation());break;}
            assertFalse(trace.air(y).violation(),"bubble tick "+i);
        }
    }
    @Test void bubbleAllowanceExpiresRatherThanExemptingFutureFlightForever() {
        var trace=new Trace();trace.at(0,80,false,true,true,false,true,.04);boolean flagged=false;
        for(int i=1;i<=220;i++)flagged|=trace.air(80+i*.3).violation();
        assertTrue(flagged);
    }
    @Test void transportPauseDoesNotConsumeTheRemainingBubbleTrajectory() {
        var trace=new Trace();trace.at(0,80,false,true,true,false,true,.04);
        for(int i=0;i<500;i++)assertFalse(trace.at(0,80,false,false,false,false,false,.04).violation());
        double y=80,v=2.7;
        for(int i=0;i<80;i++) {y+=v;v-=.04;assertFalse(trace.air(y).violation());}
    }
    @Test void supportAndWaterResetEvidenceButExemptionsCannotCreateAnchors() {
        var trace=new Trace();trace.ground(0);
        for(int i=0;i<80;i++)trace.air(82);
        assertEquals(0,trace.ground(0).evidence());
        assertFalse(trace.at(0,80,false,false,false,true,true,.04).clean());
        assertFalse(trace.ground(0).clean());assertTrue(trace.ground(0).clean());
    }
    @Test void resetAndLongOwnerGapDoNotRetainOldEvidence() {
        var trace=new Trace();trace.ground(0);
        for(int i=0;i<80;i++)trace.air(82);
        trace.model.reset();assertEquals(0,trace.air(82).evidence());
        trace.time+=1_000_000_000L;assertEquals(0,trace.air(100).evidence());
    }
    @Test void invalidCoordinatesAndCustomGravityDeferAndClearHistory() {
        var trace=new Trace();trace.ground(0);
        for(double y:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            var result=trace.air(y);assertFalse(result.violation());assertFalse(result.clean());assertEquals(0,result.evidence());
        }
        for(double g:new double[]{0,.08,Double.NaN,Double.POSITIVE_INFINITY}) {
            var result=trace.at(0,80,false,false,false,false,true,g);
            assertFalse(result.violation());assertFalse(result.clean());
        }
    }
}
