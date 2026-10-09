package dev.nordfjell.guard;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.IdentityHashMap;
import java.util.List;

/** Startup-only registry extraction. No live world, chunk or entity is consulted. */
final class NativeBlocks {
    record State(boolean supported, float friction, Geometry.Box[] shapes) {}
    private static final Geometry.Box[] EMPTY = new Geometry.Box[0];
    private static final State UNKNOWN = new State(false,.6f,EMPTY);
    private final State[] states;
    private NativeBlocks(State[] states) { this.states = states; }
    State state(int id) { return id < 0 || id >= states.length ? UNKNOWN : states[id]; }
    int size() { return states.length; }
    int supported() { int count=0; for(var state:states) if(state.supported) count++; return count; }

    static NativeBlocks bind() throws ReflectiveOperationException {
        try {
            var lookup=MethodHandles.publicLookup();
            Class<?> block=Class.forName("net.minecraft.world.level.block.Block");
            Class<?> state=Class.forName("net.minecraft.world.level.block.state.BlockState");
            Class<?> getter=Class.forName("net.minecraft.world.level.BlockGetter");
            Class<?> pos=Class.forName("net.minecraft.core.BlockPos");
            Class<?> shape=Class.forName("net.minecraft.world.phys.shapes.VoxelShape");
            Class<?> aabb=Class.forName("net.minecraft.world.phys.AABB");
            Object empty=Class.forName("net.minecraft.world.level.EmptyBlockGetter").getField("INSTANCE").get(null);
            Object zero=pos.getField("ZERO").get(null);
            Object registry=block.getField("BLOCK_STATE_REGISTRY").get(null);
            int count=(int)registry.getClass().getMethod("size").invoke(registry);
            if(count<=0 || count>131072) throw new IllegalArgumentException("Block-state registry outside bounded limits");
            MethodHandle byId=lookup.unreflect(block.getMethod("stateById",int.class)).asType(MethodType.methodType(Object.class,int.class));
            MethodHandle getBlock=objectMethod(state,"getBlock"), getFluid=objectMethod(state,"getFluidState");
            MethodHandle fluidEmpty=lookup.unreflect(Class.forName("net.minecraft.world.level.material.FluidState").getMethod("isEmpty"))
                    .asType(MethodType.methodType(boolean.class,Object.class));
            MethodHandle collision=lookup.unreflect(state.getMethod("getCollisionShape",getter,pos))
                    .asType(MethodType.methodType(Object.class,Object.class,Object.class,Object.class));
            MethodHandle boxes=objectMethod(shape,"toAabbs");
            MethodHandle[] fields=new MethodHandle[6];
            String[] names={"minX","minY","minZ","maxX","maxY","maxZ"};
            for(int i=0;i<6;i++) fields[i]=lookup.unreflectGetter(aabb.getField(names[i]))
                    .asType(MethodType.methodType(double.class,Object.class));
            var shapeCache=new IdentityHashMap<Object,Geometry.Box[]>();
            var blockCache=new IdentityHashMap<Object,Metadata>();
            var states=new State[count];
            for(int id=0;id<count;id++) {
                Object value=byId.invokeExact(id), owner=getBlock.invokeExact(value);
                Metadata metadata=blockCache.get(owner);
                if(metadata==null) {
                    String type=owner.getClass().getSimpleName();
                    boolean dynamic=(boolean)block.getMethod("hasDynamicShape").invoke(owner);
                    float friction=(float)block.getMethod("getFriction").invoke(owner);
                    float speed=(float)block.getMethod("getSpeedFactor").invoke(owner);
                    float jump=(float)block.getMethod("getJumpFactor").invoke(owner);
                    boolean ordinary=!dynamic && speed==1 && jump==1 && Float.isFinite(friction) && friction>=0 && friction<=1;
                    for(String unsupported:new String[]{"Vine","Ladder","Web","Bubble","Honey","Slime","Bed","PowderSnow",
                            "Bush","Scaffolding","Piston","Portal","SoulSand"}) ordinary &= !type.contains(unsupported);
                    metadata=new Metadata(ordinary,friction); blockCache.put(owner,metadata);
                }
                Object fluid=getFluid.invokeExact(value);
                if(!metadata.ordinary || !(boolean)fluidEmpty.invokeExact(fluid)) { states[id]=UNKNOWN; continue; }
                Object nativeShape=collision.invokeExact(value,empty,zero);
                Geometry.Box[] geometry=shapeCache.get(nativeShape);
                if(geometry==null) {
                    List<?> source=(List<?>)(Object)boxes.invokeExact(nativeShape);
                    if(source.size()>16) { states[id]=UNKNOWN;continue; }
                    geometry=new Geometry.Box[source.size()]; boolean valid=true;
                    for(int i=0;i<source.size();i++) {
                        Object box=source.get(i);
                        var g=new Geometry.Box((double)fields[0].invokeExact(box),(double)fields[1].invokeExact(box),
                                (double)fields[2].invokeExact(box),(double)fields[3].invokeExact(box),
                                (double)fields[4].invokeExact(box),(double)fields[5].invokeExact(box));
                        valid &= g.minX()>=-1 && g.minY()>=-1 && g.minZ()>=-1 && g.maxX()<=2 && g.maxY()<=2 && g.maxZ()<=2;
                        geometry[i]=g;
                    }
                    if(!valid) { states[id]=UNKNOWN;continue; }
                    shapeCache.put(nativeShape,geometry);
                }
                states[id]=new State(true,metadata.friction,geometry);
            }
            return new NativeBlocks(states);
        } catch(Throwable failure) { throw new ReflectiveOperationException("Cannot extract bounded ordinary 26.2 block geometry",failure); }
    }
    private record Metadata(boolean ordinary,float friction) {}
    private static MethodHandle objectMethod(Class<?> owner,String name) throws ReflectiveOperationException {
        return MethodHandles.publicLookup().unreflect(owner.getMethod(name)).asType(MethodType.methodType(Object.class,Object.class));
    }
}
