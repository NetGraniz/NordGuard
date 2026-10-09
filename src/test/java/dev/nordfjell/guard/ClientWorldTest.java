package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ClientWorldTest {
    private static WorldSnapshot.Chunk chunk(int x,int z,int sections,int id) {
        var source=new WorldSnapshot.Section[sections];
        for(int i=0;i<sections;i++) source[i]=new WorldSnapshot.Section(0,new int[]{id},new long[0]);
        return new WorldSnapshot.Chunk(x,z,source);
    }
    @Test void missingIsNeverAirAndNegativePositionsHaveCorrectSections() {
        var w=new ClientWorld();assertEquals(-1,w.stateId(-1,-1,-1,-4));
        w.apply(chunk(-1,-1,24,7),24,-4);
        assertEquals(7,w.stateId(-1,-1,-1,-4));assertEquals(-1,w.stateId(0,-1,-1,-4));
        assertEquals(-1,w.stateId(-1,-65,-1,-4));
        w.apply(new WorldSnapshot.Blocks(new int[]{-1,-1,-1,8}),24,-4);
        assertEquals(8,w.stateId(-1,-1,-1,-4));assertEquals(7,w.stateId(-2,-1,-1,-4));
    }
    @Test void updatesDoNotMutateSharedOutboundSnapshot() {
        var source=chunk(0,0,24,1);var first=new ClientWorld();var second=new ClientWorld();
        first.apply(source,24,-4);second.apply(source,24,-4);
        first.apply(new WorldSnapshot.Blocks(new int[]{1,80,1,42}),24,-4);
        assertEquals(42,first.stateId(1,80,1,-4));assertEquals(1,second.stateId(1,80,1,-4));
        assertEquals(1,source.section(9).stateId(1,0,1));
    }
    @Test void forgetAndUncertaintyDiscardKnowledge() {
        var w=new ClientWorld();w.apply(chunk(0,0,24,1),24,-4);w.apply(new WorldSnapshot.Forget(0,0),24,-4);
        assertEquals(-1,w.stateId(0,80,0,-4));assertEquals(0,w.bytes());
        w.apply(chunk(0,0,24,1),24,-4);w.apply(new WorldSnapshot.Invalidation(),24,-4);assertEquals(0,w.size());
        w.apply(chunk(0,0,1,1),24,-4);assertEquals(0,w.size());
    }
    @Test void chunkCountAndCopyOnWriteMemoryRemainBounded() {
        var w=new ClientWorld();
        for(int x=0;x<100;x++) w.apply(chunk(x,0,24,1),24,-4);
        assertEquals(16,w.size());assertTrue(w.bytes()<=ClientWorld.MAX_BYTES);assertEquals(84,w.evicted());
        for(int x=84;x<100;x++) for(int y=-64;y<320;y+=16)
            w.apply(new WorldSnapshot.Blocks(new int[]{x*16,y,0,2}),24,-4);
        assertTrue(w.bytes()<=ClientWorld.MAX_BYTES);assertTrue(w.invalidated()>0);
    }
    @Test void lastUsedChunkSurvivesCountEviction() {
        var w=new ClientWorld();for(int x=0;x<16;x++)w.apply(chunk(x,0,24,x),24,-4);
        assertEquals(0,w.stateId(0,80,0,-4));w.apply(chunk(16,0,24,16),24,-4);
        assertEquals(0,w.stateId(0,80,0,-4));assertEquals(-1,w.stateId(16,80,0,-4));
    }
    @Test void deniedMaterializationForgetsChunkWithoutLeavingStaleEdits() {
        var w=new ClientWorld();w.apply(chunk(0,0,24,1),24,-4);
        var costs=new java.util.ArrayList<Integer>();
        w.apply(new WorldSnapshot.Blocks(new int[]{1,80,1,42,2,80,1,43}),24,-4,cells->{costs.add(cells);return false;});
        assertEquals(java.util.List.of(4096),costs);
        assertEquals(-1,w.stateId(1,80,1,-4));assertEquals(0,w.bytes());
        w.apply(new WorldSnapshot.Blocks(new int[]{1,80,1,44}),24,-4);
        assertEquals(-1,w.stateId(1,80,1,-4));
    }
    @Test void sectionMaterializationIsChargedOnceAndRemainsPrivate() {
        var w=new ClientWorld();w.apply(chunk(0,0,24,1),24,-4);
        var charged=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.IntPredicate budget=cells->{charged.addAndGet(cells);return true;};
        w.apply(new WorldSnapshot.Blocks(new int[]{1,80,1,42}),24,-4,budget);
        w.apply(new WorldSnapshot.Blocks(new int[]{2,80,1,43}),24,-4,budget);
        assertEquals(4096,charged.get());assertEquals(42,w.stateId(1,80,1,-4));assertEquals(43,w.stateId(2,80,1,-4));
    }
    @Test void ignoredFarChangesCannotReuseOldChunkAfterReturning() {
        var w=new ClientWorld();w.apply(chunk(0,0,24,1),24,-4);w.apply(chunk(1,0,24,2),24,-4);
        w.apply(new WorldSnapshot.Retain(2,0),24,-4);
        assertEquals(-1,w.stateId(0,80,0,-4));assertEquals(2,w.stateId(16,80,0,-4));
        w.apply(new WorldSnapshot.Retain(0,0),24,-4);
        assertEquals(-1,w.stateId(0,80,0,-4));
    }
}
