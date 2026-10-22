package dev.nordfjell.guard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttackEvidenceTest {
    private static final CombatLimits LIMITS=CombatLimits.defaults();
    private static void packet(AttackEvidence q,long t) {
        q.move(t,0,80.0625,0,true);q.move(t+1,0,80,0,false);
        q.move(t+2,0,80.000011,0,false);q.move(t+3,0,80,0,false);
    }
    @Test void snapshotsEvidenceBeforeAttackNotLaterPackets() {
        var q=new AttackEvidence();var c=new AttackEvidence.Cursor();packet(q,10);q.attack(1,20,4);
        q.move(21,0,80.42,0,false);q.attack(2,22,4);
        assertTrue(q.poll(1,30,4,LIMITS,c));assertTrue(c.anomalous(LIMITS));
        assertTrue(q.poll(2,30,4,LIMITS,c));assertFalse(c.anomalous(LIMITS));
    }
    @Test void ordinaryFallingOrUngroundedRiseIsNotContradictoryGround() {
        var q=new AttackEvidence();var c=new AttackEvidence.Cursor();
        for(int i=0;i<4;i++)q.move(i,0,80.07-i*.02,0,false);
        q.attack(1,5,0);assertTrue(q.poll(1,6,0,LIMITS,c));assertFalse(c.anomalous(LIMITS));
    }
    @Test void expiredTokensAndChangedContextDefer() {
        var q=new AttackEvidence();var c=new AttackEvidence.Cursor();packet(q,0);q.attack(1,10,1);
        assertFalse(q.poll(1,20,2,LIMITS,c));assertFalse(c.valid);
        packet(q,30);q.attack(1,40,2);assertFalse(q.poll(1,300_000_041,2,LIMITS,c));
    }
    @Test void mismatchDoesNotBorrowFutureMatchingAttack() {
        var q=new AttackEvidence();var c=new AttackEvidence.Cursor();packet(q,0);q.attack(1,10,0);q.attack(2,11,0);
        assertFalse(q.poll(2,12,0,LIMITS,c));assertFalse(q.poll(2,13,0,LIMITS,c));
    }
    @Test void overflowInvalidatesRatherThanReusingOldEvidence() {
        var q=new AttackEvidence();var c=new AttackEvidence.Cursor();packet(q,0);
        for(int i=0;i<17;i++)q.attack(1,10+i,0);
        assertFalse(q.poll(1,30,0,LIMITS,c));packet(q,40);q.attack(1,50,0);
        assertTrue(q.poll(1,60,0,LIMITS,c));assertTrue(c.anomalous(LIMITS));
    }
    @Test void resetClearsMovementSummaryButNotOrdering() {
        var q=new AttackEvidence();var c=new AttackEvidence.Cursor();packet(q,0);q.resetMoves();q.attack(1,10,0);
        assertTrue(q.poll(1,11,0,LIMITS,c));assertFalse(c.anomalous(LIMITS));
    }
    @Test void unknownAndMovingGeometryDoesNotMakePacketProof() {
        var q=new AttackEvidence();var c=new AttackEvidence.Cursor();packet(q,0);q.move(5,Double.NaN,80,0,false);q.attack(1,10,0);
        assertTrue(q.poll(1,11,0,LIMITS,c));assertFalse(c.anomalous(LIMITS));
        packet(q,12);q.move(17,1,80,0,false);q.attack(1,20,0);
        assertTrue(q.poll(1,21,0,LIMITS,c));assertFalse(c.anomalous(LIMITS));
    }
    @Test void oldNonAttackMovementCannotContaminateFreshShortPattern() {
        var q=new AttackEvidence();var c=new AttackEvidence.Cursor();q.move(0,10,90,10,false);
        packet(q,300_000_000);q.attack(1,300_000_010,0);
        assertTrue(q.poll(1,300_000_020,0,LIMITS,c));assertTrue(c.anomalous(LIMITS));
    }
}
