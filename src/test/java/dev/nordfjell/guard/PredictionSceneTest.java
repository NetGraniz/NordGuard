package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PredictionSceneTest {
    private final Geometry.Point point=new Geometry.Point(.5,80,.5);
    private final Geometry.Box bounds=new Geometry.Box(0,79.9,0,1,82,1);
    @Test void collectsFloorAndFrictionWithoutLiveWorldAccess() {
        var view=PredictionScene.collect(bounds,point,true,null,false,(x,y,z)->y<80?1:0,PacketPredictionTest::state,n->true);
        assertTrue(view.known());assertTrue(view.support());assertEquals(.6f,view.friction());assertFalse(view.scene().shapes().isEmpty());
    }
    @Test void bothBranchesMustHaveKnownSupportAndUniformFriction() {
        var ice=new NativeBlocks.State(true,.98f,PacketPredictionTest.STONE.shapes());
        var view=PredictionScene.collect(new Geometry.Box(-1,79,0,2,82,1),point,true,new Geometry.Point(1.5,80,.5),true,
                (x,y,z)->y<80?(x>=1?2:1):0,id->id==2?ice:PacketPredictionTest.state(id),n->true);
        assertFalse(view.known());assertEquals("ambiguous_support_friction",view.reason());
    }
    @Test void deniedOrOversizedScenesDoNotReadAnyBlocks() {
        var reads=new AtomicInteger();PredictionScene.Blocks blocks=(x,y,z)->{reads.incrementAndGet();return 0;};
        assertFalse(PredictionScene.collect(bounds,point,true,null,false,blocks,PacketPredictionTest::state,n->false).known());
        assertFalse(PredictionScene.collect(bounds.expand(100),point,true,null,false,blocks,PacketPredictionTest::state,n->true).known());
        assertEquals(0,reads.get());
    }
    @Test void unknownHaloCannotBeAssumedEmpty() {
        assertFalse(PredictionScene.collect(bounds,point,true,null,false,(x,y,z)->x==-1?-1:0,PacketPredictionTest::state,n->true).known());
    }
    @Test void missingSecondBranchSupportDefers() {
        var view=PredictionScene.collect(bounds,point,true,new Geometry.Point(.5,81,.5),true,
                (x,y,z)->y<80?1:0,PacketPredictionTest::state,n->true);
        assertFalse(view.known());
    }
}
