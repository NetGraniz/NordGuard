package dev.nordfjell.guard;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;

/** Owner-thread ordinary packet integration. Observation only: no event cancellation or setbacks. */
final class PacketPrediction {
    static final int MAX_FRAMES_PER_DRAIN=2, REST_TICKS=5;
    private static final OrdinaryPhysics.State REST=new OrdinaryPhysics.State(0,-.08*.98f,0,0);
    record Context(boolean eligible,float speed,float jump,double gravity,float step,boolean sprinting,String dimension,
                   double ownerX,double ownerY,double ownerZ,float ownerYaw) {
        boolean ordinary() {
            return eligible && Float.isFinite(speed) && speed>0 && speed<=.4f && Float.isFinite(jump) && jump>=0 && jump<=1
                    && Double.isFinite(gravity) && gravity==.08 && Float.isFinite(step) && step>=0 && step<=1
                    && Double.isFinite(ownerX+ownerY+ownerZ) && Float.isFinite(ownerYaw) && dimension!=null;
        }
        boolean samePhysics(Context b) {
            return b!=null && eligible==b.eligible && speed==b.speed && jump==b.jump && gravity==b.gravity && step==b.step
                    && sprinting==b.sprinting && dimension.equals(b.dimension);
        }
    }
    private final OrdinaryPredictor model=new OrdinaryPredictor();
    private final PredictionScene.Blocks blocks;
    private final IntFunction<NativeBlocks.State> registry;
    private final IntPredicate cells,frames;
    private final LongSupplier worldRevision;
    private Context context;
    private boolean positioned,moved,hasPosition,badTick,rotated;
    private double x,y,z,restX,restY,restZ;
    private float yaw;
    private long moveRevision,lastTick,creditTime;
    private double tickCredit=4;
    private int rest,drainFrames;
    private long ticks,seeds,accepted,rejected,deferred,trials;
    private double lastError;
    private String reason="awaiting_context";

    PacketPrediction(PredictionScene.Blocks blocks,IntFunction<NativeBlocks.State> registry,LongSupplier worldRevision,
                     IntPredicate cells,IntPredicate frames) {
        this.blocks=blocks;this.registry=registry;this.worldRevision=worldRevision;this.cells=cells;this.frames=frames;
    }
    void refresh(Context next) {
        drainFrames=0;
        if(!next.ordinary() || !next.samePhysics(context))reset("owner_context_changed_or_unsupported");
        context=next;
    }
    void reset(String why) {model.clear();rest=0;positioned=moved=hasPosition=badTick=rotated=false;lastTick=0;reason=why;}
    void event(PacketInbox.Cursor e) {
        if(e.kind==NativePackets.CLOSED || e.kind==NativePackets.CONTEXT_CHANGE || e.kind==NativePackets.TELEPORT
                || e.kind==NativePackets.VELOCITY || e.kind==NativePackets.ATTACHED) {reset("packet_context_transition");return;}
        if(e.kind==NativePackets.WORLD_DATA && (e.payload instanceof WorldSnapshot.Reset || e.payload instanceof WorldSnapshot.Invalidation)) {
            reset("world_transition");return;
        }
        if(e.kind==NativePackets.MOVE) {
            if(moved)badTick=true;
            moved=true;hasPosition=(e.flags&1)!=0;moveRevision=worldRevision.getAsLong();
            if((e.flags&2)!=0){yaw=e.yaw;rotated=true;}
            if(hasPosition){x=e.x;y=e.y;z=e.z;positioned=true;}
        } else if(e.kind==NativePackets.TICK_END) {
            ticks++;
            boolean usable=!badTick && (!moved || moveRevision==worldRevision.getAsLong());
            try {tick(e.nano,usable);} finally {moved=hasPosition=badTick=false;}
        }
    }
    private void tick(long nano,boolean usable) {
        // TickEnd is a client claim, not a source of elapsed time. Resets never refill this budget.
        if(creditTime!=0 && nano>=creditTime)tickCredit=Math.min(4,tickCredit+(nano-creditTime)/50_000_000.0);
        if(nano<creditTime){defer("non_monotonic_tick");return;}
        creditTime=nano;
        if(tickCredit<1){defer("tick_rate_budget");return;}
        tickCredit--;
        if(lastTick!=0 && (nano<lastTick || nano-lastTick>250_000_000L)) {defer("tick_gap");lastTick=nano;return;}
        lastTick=nano;
        if(++drainFrames>MAX_FRAMES_PER_DRAIN) {defer("owner_drain_frame_limit");return;}
        if(context==null || !context.ordinary() || !positioned || !usable
                || !Double.isFinite(x+y+z) || Math.abs(x)>32_000_000 || Math.abs(z)>32_000_000 || Math.abs(y)>32768
                || !Float.isFinite(yaw)) {defer("missing_or_ambiguous_frame_context");return;}
        if(!frames.test(1)){defer("global_frame_budget");return;}
        var point=new Geometry.Point(x,y,z);
        var first=model.seeded()?model.position():point;
        var bounds=model.seeded()?model.sweep(context.speed,context.jump,context.step)
                :OrdinaryPredictor.sweep(point,REST,context.speed,context.jump,context.step);
        var view=PredictionScene.collect(bounds,first,model.seeded()?model.ground():true,
                model.alternatePosition(),model.alternateGround(),blocks,registry,cells);
        if(!view.known()){defer(view.reason());return;}
        if(!model.seeded()) {
            if(!view.support() || point.distance(new Geometry.Point(context.ownerX,context.ownerY,context.ownerZ))>.031) {
                defer("unconfirmed_rest_origin");return;
            }
            boolean same=rest>0 && point.distance(new Geometry.Point(restX,restY,restZ))<1E-7;
            rest=same?rest+1:1;restX=x;restY=y;restZ=z;
            if(rest<REST_TICKS){reason="establishing_supported_rest";return;}
            model.seed(x,y,z,REST,true);seeds++;reason="seeded_supported_rest";return;
        }
        var physics=new OrdinaryPhysics.Context(model.ground(),context.sprinting,rotated?yaw:context.ownerYaw,
                context.speed,context.jump,context.gravity,view.friction(),1,1,1,.3f,false);
        var result=model.accept(new OrdinaryPredictor.Frame(x,y,z,physics.yaw(),hasPosition),physics,view.scene(),context.step);
        trials+=result.candidates();lastError=result.error();reason=result.reason();
        switch(result.status()) {
            case ACCEPT -> accepted++;
            case REJECT -> {rejected++;if(result.consecutiveRejected()>=8){model.clear();rest=0;reason="repeated_mismatch_reacquire_rest";}}
            case DEFER -> {deferred++;rest=0;}
        }
    }
    private void defer(String why) {model.clear();rest=0;deferred++;reason=why;}
    long accepted() {return accepted;}
    long rejected() {return rejected;}
    long seeds() {return seeds;}
    long trials() {return trials;}
    String diagnostic() {
        return "Prediction observe-only, ticks="+ticks+", seeds="+seeds+", accepted="+accepted+", mismatched="+rejected
                +", deferred="+deferred+", trials="+trials+", branches="+model.branches()+", last error="+lastError+", state="+reason;
    }
}
