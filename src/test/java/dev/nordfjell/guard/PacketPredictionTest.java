package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PacketPredictionTest {
    static final NativeBlocks.State AIR=new NativeBlocks.State(true,.6f,new Geometry.Box[0]);
    static final NativeBlocks.State STONE=new NativeBlocks.State(true,.6f,new Geometry.Box[]{new Geometry.Box(0,0,0,1,1,1)});
    static NativeBlocks.State state(int id) {return id==0?AIR:id==1?STONE:new NativeBlocks.State(false,.6f,new Geometry.Box[0]);}
    static PacketPrediction.Context context(double x,double y,double z) {
        return new PacketPrediction.Context(true,.1f,.42f,.08,.6f,false,"minecraft:overworld",x,y,z,0);
    }
    static final class Fixture {
        final PacketInbox q=new PacketInbox();final PacketInbox.Cursor e=new PacketInbox.Cursor();
        long nano=1_000_000_000,revision;boolean known=true,allowCells=true,allowFrames=true;
        final PacketPrediction p=new PacketPrediction((x,y,z)->known?(y<80?1:0):-1,PacketPredictionTest::state,
                ()->revision,c->allowCells,f->allowFrames);
        void emit(int kind,int flags,double x,double y,double z) {
            q.event(kind,nano,0,flags,x,y,z,0,0);assertTrue(q.poll(e));p.event(e);
        }
        void frame(double x,double y,double z) {
            nano+=50_000_000;p.refresh(context(x,y,z));emit(NativePackets.MOVE,3,x,y,z);emit(NativePackets.TICK_END,0,0,0,0);
        }
        void rest() {for(int i=0;i<5;i++)frame(.5,80,.5);assertEquals(1,p.seeds(),p.diagnostic());}
    }
    @Test void realEventAssemblySeedsOnlyAfterFiveSupportedRestFrames() {
        var f=new Fixture();for(int i=0;i<4;i++)f.frame(.5,80,.5);assertEquals(0,f.p.seeds());
        f.frame(.5,80,.5);assertEquals(1,f.p.seeds());f.frame(.5,80,.5);assertEquals(1,f.p.accepted());
    }
    @Test void ordinaryWalkUsesIntegratedSceneAndPreservesCalculatedMomentum() {
        var f=new Fixture();f.rest();double z=.5;var v=new OrdinaryPhysics.State(0,-.08*.98f,0,0);
        var c=new OrdinaryPhysics.Context(true,false,0,.1f,.42f,.08,.6f,1,1,1,.3f,false);
        for(int i=0;i<200;i++) {
            var free=OrdinaryPhysics.step(v,new OrdinaryPhysics.Input(0,1,false),c);
            z+=free.dz();v=new OrdinaryPhysics.State(free.next().vx(),-.08*.98f,free.next().vz(),free.next().jumpDelay());
            f.frame(.5,80,z);
        }
        assertEquals(200,f.p.accepted(),f.p.diagnostic());assertEquals(0,f.p.rejected());assertEquals(3600,f.p.trials());
    }
    @Test void omittedMovesStillAdvanceStandingClientTicks() {
        var f=new Fixture();f.rest();
        for(int i=0;i<20;i++){f.nano+=50_000_000;f.p.refresh(context(.5,80,.5));f.emit(NativePackets.TICK_END,0,0,0,0);}
        assertEquals(20,f.p.accepted());assertEquals(0,f.p.rejected());
    }
    @Test void ordinaryJumpAndLandingUseCollectedFloorNotClientGroundClaim() {
        var f=new Fixture();f.rest();double y=80,z=.5;boolean ground=true;
        var v=new OrdinaryPhysics.State(0,-.08*.98f,0,0);
        for(int i=0;i<30;i++) {
            var context=new OrdinaryPhysics.Context(ground,false,0,.1f,.42f,.08,.6f,1,1,1,.3f,false);
            var step=OrdinaryPhysics.step(v,new OrdinaryPhysics.Input(0,1,i==0),context);
            z+=step.dz();y+=step.dy();ground=y<=80;
            if(ground)y=80;
            v=new OrdinaryPhysics.State(step.next().vx(),ground?-.08*.98f:step.next().vy(),step.next().vz(),step.next().jumpDelay());
            f.frame(.5,y,z);
        }
        assertEquals(30,f.p.accepted(),f.p.diagnostic());assertEquals(0,f.p.rejected());
    }
    @Test void fixedSprintContextRunsPacketPredictionWithoutTrustingInputBits() {
        var f=new Fixture();double z=.5;var v=new OrdinaryPhysics.State(0,-.08*.98f,0,0);
        var c=new OrdinaryPhysics.Context(true,true,0,.13f,.42f,.08,.6f,1,1,1,.3f,false);
        for(int i=0;i<35;i++) {
            if(i>=5){var step=OrdinaryPhysics.step(v,new OrdinaryPhysics.Input(0,1,false),c);z+=step.dz();v=step.next();}
            f.nano+=50_000_000;f.p.refresh(new PacketPrediction.Context(true,.13f,.42f,.08,.6f,true,"minecraft:overworld",.5,80,z,0));
            f.emit(NativePackets.MOVE,3,.5,80,z);f.emit(NativePackets.TICK_END,0,0,0,0);
        }
        assertEquals(30,f.p.accepted(),f.p.diagnostic());assertEquals(0,f.p.rejected());
    }
    @Test void unsupportedOrUnacknowledgedWorldNeverReportsMismatch() {
        var f=new Fixture();f.rest();f.known=false;f.frame(.5,80,2);assertEquals(0,f.p.rejected());
        assertTrue(f.p.diagnostic().contains("unknown_or_unsupported_geometry"));
    }
    @Test void largeGroundClaimWithoutActualSupportCannotSeed() {
        var f=new Fixture();for(int i=0;i<10;i++)f.frame(.5,81,.5);
        assertEquals(0,f.p.seeds());assertEquals(0,f.p.rejected());assertEquals(0,f.p.trials());
    }
    @Test void ownerDisagreementCannotSeedFromUncheckedPosition() {
        var f=new Fixture();
        for(int i=0;i<10;i++) {
            f.nano+=50_000_000;f.p.refresh(context(.5,80,.5));f.emit(NativePackets.MOVE,3,20,80,20);f.emit(NativePackets.TICK_END,0,0,0,0);
        }
        assertEquals(0,f.p.seeds());
    }
    @Test void duplicateMovesAndMissingTickEndNeverRunExtraTrials() {
        var f=new Fixture();f.rest();f.nano+=50_000_000;f.p.refresh(context(.5,80,.5));
        for(int i=0;i<100;i++)f.emit(NativePackets.MOVE,3,.5,80,.5);
        assertEquals(0,f.p.trials());f.emit(NativePackets.TICK_END,0,0,0,0);assertEquals(0,f.p.trials());
        assertEquals(0,f.p.rejected());
    }
    @Test void worldCommitBetweenMoveAndTickEndDefersAmbiguousFrame() {
        var f=new Fixture();f.rest();f.nano+=50_000_000;f.p.refresh(context(.5,80,.5));
        f.emit(NativePackets.MOVE,3,.5,80,.5);f.revision++;f.emit(NativePackets.TICK_END,0,0,0,0);
        assertEquals(0,f.p.accepted());assertEquals(0,f.p.rejected());
    }
    @Test void allContextTransitionsClearTheSeed() {
        for(int kind:new int[]{NativePackets.CLOSED,NativePackets.CONTEXT_CHANGE,NativePackets.TELEPORT,NativePackets.VELOCITY}) {
            var f=new Fixture();f.rest();f.emit(kind,0,0,0,0);f.frame(.5,80,.5);assertEquals(0,f.p.trials());
        }
    }
    @Test void budgetsDeferWithoutMismatch() {
        for(boolean frameBudget:new boolean[]{true,false}) {
            var f=new Fixture();f.rest();if(frameBudget)f.allowFrames=false;else f.allowCells=false;
            f.frame(.5,80,3);assertEquals(0,f.p.trials());assertEquals(0,f.p.rejected());
            assertTrue(f.p.diagnostic().contains(frameBudget?"global_frame_budget":"scene_global_budget"));
        }
    }
    @Test void ownerDrainHasAtMostTwoPredictionFrames() {
        var f=new Fixture();f.rest();f.p.refresh(context(.5,80,.5));
        for(int i=0;i<10;i++){f.nano+=50_000_000;f.emit(NativePackets.MOVE,3,.5,80,.5);f.emit(NativePackets.TICK_END,0,0,0,0);}
        assertEquals(2,f.p.accepted());assertEquals(36,f.p.trials());
    }
    @Test void tickClaimsAndContextResetsCannotBuyElapsedTime() {
        var f=new Fixture();f.rest();
        for(int i=0;i<100;i++) {
            f.p.reset("injected_transition");f.p.refresh(context(.5,80,.5));
            f.emit(NativePackets.MOVE,3,.5,80,.5);f.emit(NativePackets.TICK_END,0,0,0,0);
        }
        assertEquals(1,f.p.seeds());assertTrue(f.p.diagnostic().contains("tick_rate_budget"));
    }
    @Test void longGapAndNonFinitePacketDoNotProduceMismatch() {
        var f=new Fixture();f.rest();f.nano+=500_000_000;f.frame(.5,80,.5);assertEquals(0,f.p.trials());
        f.frame(Double.NaN,80,.5);assertEquals(0,f.p.rejected());
    }
    @Test void excessMovementRecordsMismatchWithoutReplacingInternalVelocity() {
        var f=new Fixture();f.rest();for(int i=0;i<5;i++)f.frame(.5,80,.5+i*.8);
        assertTrue(f.p.rejected()>=3,f.p.diagnostic());assertEquals(1,f.p.seeds());
    }
    @Test void unsupportedOwnerAttributesDefer() {
        var f=new Fixture();f.rest();f.p.refresh(new PacketPrediction.Context(true,2,.42f,.08,.6f,false,"minecraft:overworld",.5,80,.5,0));
        f.emit(NativePackets.TICK_END,0,0,0,0);assertEquals(0,f.p.trials());
    }
    @Test void teleportRequiresMatchingAcknowledgementBeforeRestAcquisition() {
        var f=new Fixture();f.rest();f.emit(NativePackets.TELEPORT,0,0,0,0);
        for(int i=0;i<20;i++)f.frame(.5,80,.5);
        assertEquals(1,f.p.seeds());assertEquals(0,f.p.trials());
        f.emit(NativePackets.TELEPORT_ACK,0,0,0,0);
        for(int i=0;i<5;i++)f.frame(.5,80,.5);
        assertEquals(2,f.p.seeds());assertEquals(0,f.p.rejected());
    }
    @Test void staleAckCannotReleaseNewTeleport() {
        var f=new Fixture();f.rest();
        f.q.event(NativePackets.TELEPORT,f.nano,41,0,0,0,0,0,0);assertTrue(f.q.poll(f.e));f.p.event(f.e);
        f.q.event(NativePackets.TELEPORT,f.nano,42,0,0,0,0,0,0);assertTrue(f.q.poll(f.e));f.p.event(f.e);
        f.q.event(NativePackets.TELEPORT_ACK,f.nano,41,0,0,0,0,0,0);assertTrue(f.q.poll(f.e));f.p.event(f.e);
        for(int i=0;i<10;i++)f.frame(.5,80,.5);
        assertEquals(1,f.p.seeds());assertEquals(0,f.p.trials());
        f.q.event(NativePackets.TELEPORT_ACK,f.nano,42,0,0,0,0,0,0);assertTrue(f.q.poll(f.e));f.p.event(f.e);
        for(int i=0;i<5;i++)f.frame(.5,80,.5);assertEquals(2,f.p.seeds());
    }
    @Test void ownerAndVelocityResetsCannotClearPendingTeleport() {
        var f=new Fixture();f.rest();f.emit(NativePackets.TELEPORT,0,0,0,0);
        f.p.reset("owner_transition");f.emit(NativePackets.VELOCITY,0,0,0,0);
        for(int i=0;i<10;i++)f.frame(.5,80,.5);
        assertEquals(1,f.p.seeds());assertEquals(0,f.p.trials());
    }
    @Test void unsupportedOwnerStateClearsMomentumAndNeedsNewRest() {
        var f=new Fixture();f.rest();
        f.p.refresh(new PacketPrediction.Context(false,.1f,.42f,.08,.6f,false,"minecraft:overworld",.5,80,.5,0));
        f.emit(NativePackets.TICK_END,0,0,0,0);
        for(int i=0;i<4;i++)f.frame(.5,80,.5);
        assertEquals(1,f.p.seeds());f.frame(.5,80,.5);assertEquals(2,f.p.seeds());assertEquals(0,f.p.rejected());
    }
    @Test void lateEnabledPredictorInheritsOutstandingTeleportFromTimeline() {
        var f=new Fixture();f.p.awaitTeleport(23);
        for(int i=0;i<10;i++)f.frame(.5,80,.5);assertEquals(0,f.p.seeds());
        f.q.event(NativePackets.TELEPORT_ACK,f.nano,23,0,0,0,0,0,0);assertTrue(f.q.poll(f.e));f.p.event(f.e);
        for(int i=0;i<5;i++)f.frame(.5,80,.5);assertEquals(1,f.p.seeds());
    }
}
