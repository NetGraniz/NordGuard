package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SetbackWindowTest {
    @Test void matchingCompletionConsumedOnce() {
        var w=new SetbackWindow();long t=w.begin(100,3);
        assertTrue(w.finish(t,3,101));assertFalse(w.finish(t,3,102));
    }
    @Test void externalOriginDoesNotConsumeCurrentTicket() {
        var w=new SetbackWindow();long t=w.begin(100,3);
        assertFalse(w.finish(t,4,101));assertTrue(w.owns(t,3));
    }
    @Test void cancelRejectsLateCompletion() {
        var w=new SetbackWindow();long t=w.begin(100,3);w.cancel();
        assertFalse(w.finish(t,3,101));assertFalse(w.expired(Long.MAX_VALUE));
    }
    @Test void oldCompletionCannotConsumeReplacement() {
        var w=new SetbackWindow();long old=w.begin(100,3),next=w.begin(200,3);
        assertFalse(w.finish(old,3,201));assertTrue(w.finish(next,3,202));
    }
    @Test void timeoutBoundaryRejectsCompletion() {
        var w=new SetbackWindow();long t=w.begin(100,3);
        assertFalse(w.expired(100+SetbackWindow.TIMEOUT_NANOS-1));
        assertTrue(w.expired(100+SetbackWindow.TIMEOUT_NANOS));
        assertFalse(w.finish(t,3,100+SetbackWindow.TIMEOUT_NANOS));assertFalse(w.owns(t,3));
    }
    @Test void backwardClockFailsClosed() {
        var w=new SetbackWindow();long t=w.begin(100,3);
        assertTrue(w.expired(99));assertFalse(w.finish(t,3,99));
    }
    @Test void anchorAgeHasStrictBound() {
        assertTrue(SetbackWindow.freshAnchor(100,100,0));
        assertTrue(SetbackWindow.freshAnchor(100+SetbackWindow.ANCHOR_NANOS-1,100,0));
        assertFalse(SetbackWindow.freshAnchor(100+SetbackWindow.ANCHOR_NANOS,100,0));
    }
    @Test void missingOrFutureAnchorRejected() {
        assertFalse(SetbackWindow.freshAnchor(100,0,0));assertFalse(SetbackWindow.freshAnchor(100,101,0));
    }
    @Test void distanceBoundAndNonFiniteAnchorsRejected() {
        assertTrue(SetbackWindow.freshAnchor(100,100,4096));
        for(double d:new double[]{4096.01,-1,Double.NaN,Double.POSITIVE_INFINITY})assertFalse(SetbackWindow.freshAnchor(100,100,d));
    }
    @Test void movementBetweenTeleportAndOwnerCompletionDoesNotRequireExactPositionEquality() {
        assertTrue(SetbackWindow.nearby(1.5*1.5));
        assertFalse(SetbackWindow.nearby(Double.NaN));assertFalse(SetbackWindow.nearby(4097));
    }
}
