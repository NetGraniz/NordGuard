package dev.nordfjell.guard;

import java.util.ArrayList;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/** Bounded collection from acknowledged IDs only; never consults the live world. */
final class PredictionScene {
    static final int MAX_CELLS=512;
    @FunctionalInterface interface Blocks { int stateId(int x,int y,int z); }
    record View(CollisionPhysics.Scene scene,float friction,boolean support,String reason) {
        boolean known() {return scene!=null;}
    }
    private static final View UNKNOWN=new View(null,.6f,false,"unknown_or_unsupported_geometry");
    private PredictionScene() {}

    static View collect(Geometry.Box sweep,Geometry.Point first,boolean firstGround,
                        Geometry.Point second,boolean secondGround,Blocks blocks,
                        IntFunction<NativeBlocks.State> registry,IntPredicate budget) {
        // Shapes may extend one cell beyond their block. Include that halo before charging/reading.
        int x0=(int)Math.floor(sweep.minX())-1,x1=(int)Math.floor(sweep.maxX())+1;
        int y0=(int)Math.floor(sweep.minY())-1,y1=(int)Math.floor(sweep.maxY())+1;
        int z0=(int)Math.floor(sweep.minZ())-1,z1=(int)Math.floor(sweep.maxZ())+1;
        long cells=((long)x1-x0+1)*((long)y1-y0+1)*((long)z1-z0+1);
        if(cells<=0 || cells>MAX_CELLS)return new View(null,.6f,false,"scene_cell_limit");
        if(!budget.test((int)cells))return new View(null,.6f,false,"scene_global_budget");
        var shapes=new ArrayList<Geometry.Box>();
        Float friction=null;boolean supportA=false,supportB=false;
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++) {
            var state=registry.apply(blocks.stateId(x,y,z));
            if(state==null || !state.supported())return UNKNOWN;
            for(var local:state.shapes()) {
                var box=new Geometry.Box(x+local.minX(),y+local.minY(),z+local.minZ(),
                        x+local.maxX(),y+local.maxY(),z+local.maxZ());
                if(!touches(box,sweep))continue;
                if(shapes.size()==CollisionPhysics.MAX_SHAPES)return new View(null,.6f,false,"scene_shape_limit");
                shapes.add(box);
                boolean a=support(box,first),b=second!=null && support(box,second);
                supportA|=a;supportB|=b;
                if(a && firstGround || b && secondGround) {
                    if(friction!=null && Float.compare(friction,state.friction())!=0)
                        return new View(null,.6f,false,"ambiguous_support_friction");
                    friction=state.friction();
                }
            }
        }
        if(firstGround && !supportA || secondGround && !supportB)return UNKNOWN;
        return new View(new CollisionPhysics.Scene(shapes),friction==null?.6f:friction,supportA,"known_static_scene");
    }
    private static boolean support(Geometry.Box box,Geometry.Point p) {
        return Math.abs(box.maxY()-p.y())<1E-6 && box.maxX()>p.x()-.3+1E-7 && box.minX()<p.x()+.3-1E-7
                && box.maxZ()>p.z()-.3+1E-7 && box.minZ()<p.z()+.3-1E-7;
    }
    private static boolean touches(Geometry.Box a,Geometry.Box b) {
        return a.maxX()>=b.minX() && a.minX()<=b.maxX() && a.maxY()>=b.minY() && a.minY()<=b.maxY()
                && a.maxZ()>=b.minZ() && a.minZ()<=b.maxZ();
    }
}
