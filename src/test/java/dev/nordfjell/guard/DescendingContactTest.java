package dev.nordfjell.guard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DescendingContactTest {
    private static MovementModel.Frame frame(double x,double y,boolean ground) {
        return new MovementModel.Frame(x,y,x,ground,false,false,.286,.41999998688697815,.6,.08);
    }

    private static MovementModel approach(double support) {
        var model=new MovementModel();var policy=MovementModelTest.policy();
        model.accept(frame(0,80,true),policy,80);
        model.accept(frame(.2,79,false),policy);
        for(int i=0;i<12;i++)model.transportIdleTick();
        model.accept(frame(.4,70.162141421027,false),policy,support);
        // A native collision can land on the ledge while horizontal motion
        // has already moved the final feet box beyond that ledge.
        model.accept(frame(.6,70.01,false),policy,68);
        return model;
    }

    @Test void recentLedgeContactSurvivesLowerFloorInFinalFeetBox() {
        var result=approach(70).accept(frame(.9,70.41999998688698,false),MovementModelTest.policy(),68);
        assertTrue(result.inferredLanding());
        assertEquals(10,result.landingDistance(),1E-8);
    }

    @Test void unknownLedgeDoesNotInventLanding() {
        assertFalse(approach(Double.NaN).accept(frame(.9,70.41999998688698,false),MovementModelTest.policy(),68).inferredLanding());
    }

    @Test void wrongHeightCannotGrantLanding() {
        assertFalse(approach(69).accept(frame(.9,70.41999998688698,false),MovementModelTest.policy(),68).inferredLanding());
    }

    @Test void expiredLedgeCannotRestartFlightAfterTransportPause() {
        var model=approach(70);
        for(int i=0;i<7;i++)model.transportIdleTick();
        assertFalse(model.accept(frame(.9,70.41999998688698,false),MovementModelTest.policy(),68).inferredLanding());
    }

    @Test void invalidTakeoffCannotUseRecentLedge() {
        assertFalse(approach(70).accept(frame(.9,70.1,false),MovementModelTest.policy(),68).inferredLanding());
    }

    @Test void passiveSweptContactSplitsFallsWithoutErasingDamage() {
        var model=approach(70);var policy=MovementModelTest.policy();
        var contact=model.accept(frame(.7,70,false),policy,68);
        assertTrue(contact.inferredLanding());assertEquals(10,contact.landingDistance(),1E-8);
        model.accept(frame(.8,69.92,false),policy,68);
        var finalContact=model.accept(frame(.9,68,true),policy,68);
        assertEquals(2,finalContact.landingDistance(),1E-8);
    }

    @Test void passiveContactRequiresExactKnownFloor() {
        assertFalse(approach(Double.NaN).accept(frame(.7,70,false),MovementModelTest.policy(),68).inferredLanding());
        assertFalse(approach(70).accept(frame(.7,69.95,false),MovementModelTest.policy(),68).inferredLanding());
    }
}
