package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class AcknowledgedWorldTest {
    private static WorldSnapshot.Chunk chunk(int x,int z,int id) {
        var sections=new WorldSnapshot.Section[24];
        for(int i=0;i<24;i++)sections[i]=new WorldSnapshot.Section(0,new int[]{id},new long[0]);
        return new WorldSnapshot.Chunk(x,z,sections);
    }
    private static WorldSnapshot.Blocks block(int x,int id) {return new WorldSnapshot.Blocks(new int[]{x,80,0,id});}
    private static final class Fixture {
        final ClientWorld raw=new ClientWorld();
        final AcknowledgedWorld w=new AcknowledgedWorld(raw,-4,24,"minecraft:overworld",0,0,n->true,n->true);
        void sent(int id,long time) {w.event(NativePackets.BARRIER_SENT,id,time);}
        void pong(int id,long time) {w.event(NativePackets.PONG,id,time);}
        void seed() {w.stage(chunk(0,0,1));sent(1,100);pong(1,200);}
        int value() {return w.stateId(0,80,0);}
    }
    @Test void dataNeverBecomesKnownBeforeMatchingPong() {
        var f=new Fixture();f.w.stage(chunk(0,0,1));assertEquals(0,f.raw.size());assertEquals(-1,f.value());
        f.sent(7,100);f.pong(99,200);assertEquals(0,f.raw.size());assertEquals(-1,f.value());
        f.pong(7,300);assertEquals(1,f.value());assertEquals(0,f.w.pending());assertEquals(0,f.w.bytes());
    }
    @Test void oldBarrierCannotConfirmNewerUpdates() {
        var f=new Fixture();f.seed();f.sent(2,300);f.w.stage(block(0,2));f.pong(2,400);
        assertEquals(1,f.raw.stateId(0,80,0,-4));assertEquals(-1,f.value());assertEquals(1,f.w.pending());
        f.sent(3,500);f.pong(3,600);assertEquals(2,f.value());
    }
    @Test void acknowledgementAppliesOnlyItsPrefixInOrder() {
        var f=new Fixture();f.seed();f.w.stage(block(0,2));f.sent(2,300);f.w.stage(block(0,3));f.sent(3,400);
        f.pong(2,500);assertEquals(2,f.raw.stateId(0,80,0,-4));assertEquals(-1,f.value());
        f.pong(3,600);assertEquals(3,f.value());
    }
    @Test void dirtyChunkDoesNotHideUnrelatedConfirmedChunk() {
        var f=new Fixture();f.w.stage(chunk(0,0,1));f.w.stage(chunk(1,0,2));f.sent(1,100);f.pong(1,200);
        f.w.stage(block(0,3));assertEquals(-1,f.value());assertEquals(2,f.w.stateId(16,80,0));
    }
    @Test void reorderedKnownReplyClearsWorldAndOldRepliesCannotRestoreIt() {
        var f=new Fixture();f.seed();f.w.stage(block(0,2));f.sent(2,300);f.sent(3,400);f.pong(3,500);
        assertEquals(0,f.raw.size());assertEquals(0,f.w.pending());f.pong(2,600);assertEquals(-1,f.value());
        f.w.stage(chunk(0,0,4));f.sent(4,700);f.pong(4,800);assertEquals(4,f.value());
    }
    @Test void expiredOrBackwardReplyDoesNotCommit() {
        for(long reply:new long[]{99,100+PacketTimeline.TIMEOUT+1}) {
            var f=new Fixture();f.w.stage(chunk(0,0,1));f.sent(1,100);f.pong(1,reply);
            assertEquals(0,f.raw.size());assertEquals(0,f.w.pending());assertEquals(-1,f.value());
        }
    }
    @Test void timeoutFreesPendingPayloadAndConfirmedData() {
        var f=new Fixture();f.seed();f.w.stage(block(0,2));f.sent(2,300);f.w.expire(300+PacketTimeline.TIMEOUT+1);
        assertEquals(0,f.raw.size());assertEquals(0,f.w.bytes());f.pong(2,301);assertEquals(-1,f.value());
    }
    @Test void duplicateAndReusedIdsCannotAdvanceWorld() {
        var f=new Fixture();f.seed();f.w.stage(block(0,2));f.pong(1,300);assertEquals(-1,f.value());
        f.sent(1,400);f.pong(1,500);assertEquals(0,f.raw.size());assertEquals(0,f.w.pending());
    }
    @Test void pendingAndBarrierCountsStayBounded() {
        var f=new Fixture();f.seed();for(int i=0;i<16;i++)f.w.stage(block(i,i));
        assertEquals(16,f.w.pending());f.w.stage(block(0,20));assertEquals(0,f.w.pending());assertEquals(0,f.raw.size());
        for(int i=2;i<7;i++)f.sent(i,1000+i);f.pong(2,2000);assertEquals(-1,f.value());
    }
    @Test void journalRejectsOversizedPayloadBeforeDecodingOrReserving() {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var w=new AcknowledgedWorld(new ClientWorld(),-4,24,"minecraft:overworld",0,0,n->{calls.incrementAndGet();return true;},n->true);
        w.stage(new WorldSnapshot.EncodedChunk(0,0,new byte[AcknowledgedWorld.MAX_BYTES]));
        assertEquals(0,calls.get());assertEquals(0,w.bytes());assertEquals(0,w.pending());
    }
    @Test void aggregateJournalByteLimitClearsBeforeEntryLimit() {
        var f=new Fixture();f.seed();var entries=new int[4096*4];
        for(int i=0;i<4096;i++){entries[i*4]=i&15;entries[i*4+1]=80+(i>>8);entries[i*4+2]=(i>>4)&15;entries[i*4+3]=2;}
        var update=new WorldSnapshot.Blocks(entries);
        for(int i=0;i<7;i++)f.w.stage(update);
        assertEquals(7,f.w.pending());assertEquals(7*update.estimatedBytes(),f.w.bytes());
        f.w.stage(update);assertEquals(0,f.w.pending());assertEquals(0,f.w.bytes());assertEquals(-1,f.value());
    }
    @Test void repeatedPartialCommitsWrapJournalWithoutRetainingOldPayloads() {
        var f=new Fixture();f.seed();
        for(int id=2;id<102;id++) {
            f.w.stage(block(0,id));f.sent(id,id*100L);f.pong(id,id*100L+1);
            assertEquals(id,f.value());assertEquals(0,f.w.pending());assertEquals(0,f.w.bytes());
        }
    }
    @Test void decodeBudgetAndMalformedDataFailUnknown() {
        var f=new Fixture();f.seed();f.w.stage(new WorldSnapshot.EncodedChunk(0,0,new byte[]{1}));assertEquals(-1,f.value());
        var raw=new ClientWorld();var w=new AcknowledgedWorld(raw,-4,24,"minecraft:overworld",0,0,n->false,n->true);
        w.stage(new WorldSnapshot.EncodedChunk(0,0,new byte[]{1}));assertEquals(0,w.pending());assertEquals(0,raw.size());
    }
    @Test void encodedDataDecodesBeforeBarrierButAppliesOnlyAfterPong() {
        var f=new Fixture();var payload=new byte[24*8];
        for(int i=0;i<24;i++){payload[i*8]=16;payload[i*8+5]=1;}
        f.w.stage(new WorldSnapshot.EncodedChunk(0,0,payload));assertEquals(1,f.w.pending());assertEquals(0,f.raw.decoded());
        f.sent(1,100);f.pong(1,200);assertEquals(1,f.value());assertEquals(1,f.raw.decoded());
    }
    @Test void dimensionResetCancelsOldBarriersEvenWhenHeightsMatch() {
        var f=new Fixture();f.seed();f.w.stage(block(0,2));f.sent(2,300);
        f.w.stage(new WorldSnapshot.Reset(-4,24,"custom:other"));assertEquals("custom:other",f.w.dimension());
        f.w.stage(chunk(0,0,3));f.pong(2,400);assertEquals(-1,f.value());
        f.sent(3,500);f.pong(3,600);assertEquals(3,f.value());
    }
    @Test void unknownDimensionAndWrongSectionCountCannotBecomeKnown() {
        var f=new Fixture();f.seed();f.w.stage(new WorldSnapshot.Reset());f.w.stage(chunk(0,0,2));
        f.sent(2,300);f.pong(2,400);assertEquals(-1,f.value());
        f.w.stage(new WorldSnapshot.Reset(0,16,"minecraft:the_nether"));f.w.stage(chunk(0,0,2));
        f.sent(3,500);f.pong(3,600);assertEquals(-1,f.value());assertEquals(0,f.raw.size());
    }
    @Test void areaPruningCannotReintroduceStaleFarSnapshotsOnAck() {
        var f=new Fixture();f.w.stage(chunk(-1,0,1));f.sent(1,100);f.w.stage(new WorldSnapshot.Retain(1,0));
        f.pong(1,200);assertEquals(0,f.raw.size());f.w.stage(new WorldSnapshot.Retain(0,0));
        assertEquals(-1,f.w.stateId(-16,80,0));
    }
    @Test void malformedCrossChunkBatchAndOutOfHeightBlocksInvalidate() {
        for(var update:new WorldSnapshot.Blocks[]{new WorldSnapshot.Blocks(new int[]{0,80,0,2,16,80,0,2}),
                new WorldSnapshot.Blocks(new int[]{0,320,0,2})}) {
            var f=new Fixture();f.seed();f.w.stage(update);assertEquals(-1,f.value());assertEquals(0,f.raw.size());
        }
    }
    @Test void deniedMaterializationLeavesUnknownNotOldBlocks() {
        var raw=new ClientWorld();var w=new AcknowledgedWorld(raw,-4,24,"minecraft:overworld",0,0,n->true,n->false);
        w.stage(chunk(0,0,1));w.event(NativePackets.BARRIER_SENT,1,100);w.event(NativePackets.PONG,1,200);
        w.stage(block(0,2));w.event(NativePackets.BARRIER_SENT,2,300);w.event(NativePackets.PONG,2,400);
        assertEquals(-1,w.stateId(0,80,0));assertEquals(0,raw.size());
    }
    @Test void streamCallbackSeesCorrectHistoricalPrefixAtMovement() {
        var f=new Fixture();f.seed();var observations=new ArrayList<Integer>();
        var t=new PacketTimeline(f.w::stage,e->{f.w.event(e.kind,e.id,e.nano);if(e.kind==NativePackets.MOVE)observations.add(f.value());});
        var q=new PacketInbox(n->true);q.world(block(0,2));q.event(NativePackets.MOVE,300,0,0,0,0,0,0,0);
        q.event(NativePackets.BARRIER_SENT,400,2,0,0,0,0,0,0);q.event(NativePackets.MOVE,450,0,0,0,0,0,0,0);
        q.event(NativePackets.PONG,500,2,0,0,0,0,0,0);q.event(NativePackets.MOVE,600,0,0,0,0,0,0,0);
        t.drain(q,700);assertEquals(java.util.List.of(-1,-1,2),observations);
    }
    @Test void inboxOverflowClearsWorldAndDiscardedRepliesCannotCommit() {
        var f=new Fixture();f.seed();f.w.stage(block(0,2));f.sent(2,300);
        var t=new PacketTimeline(f.w::stage,e->f.w.event(e.kind,e.id,e.nano));var q=new PacketInbox(n->true);
        for(int i=0;i<300;i++)q.event(NativePackets.PONG,400,2,0,0,0,0,0,0);
        t.drain(q,500);t.drain(q,600);assertEquals(-1,f.value());assertEquals(0,f.w.bytes());
    }
    @Test void closeAndContextAmbiguityDiscardConfirmedAndInFlightData() {
        for(int kind:new int[]{NativePackets.CLOSED,NativePackets.CONTEXT_CHANGE}) {
            var f=new Fixture();f.seed();f.w.stage(block(0,2));f.sent(2,300);f.w.event(kind,0,400);f.pong(2,500);
            assertEquals(-1,f.value());assertEquals(0,f.w.pending());assertEquals(0,f.raw.size());
        }
    }
}
