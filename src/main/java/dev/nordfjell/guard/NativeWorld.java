package dev.nordfjell.guard;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.function.IntPredicate;

/** Captures only packet-owned values; never queries a level, entity or live chunk. */
final class NativeWorld {
    private static final String GAME="net.minecraft.network.protocol.game.";
    private static final MethodHandles.Lookup LOOKUP=MethodHandles.publicLookup();
    @FunctionalInterface interface ChunkInterest { boolean test(int x,int z); }
    private static final ChunkInterest ALL_CHUNKS=(x,z)->true;
    private final Class<?> chunk,block,section,forget,respawn,login,blockEvent;
    private final MethodHandle chunkX,chunkZ,chunkReady,chunkData,chunkBuffer;
    private final MethodHandle blockPos,blockState,posX,posY,posZ,stateId;
    private final MethodHandle sectionPos,sectionPositions,sectionStates,relativeX,relativeY,relativeZ;
    private final MethodHandle forgetPos,forgetX,forgetZ;
    private final MethodHandle blockEventPos;

    static NativeWorld bind() throws ReflectiveOperationException { return new NativeWorld(); }
    private NativeWorld() throws ReflectiveOperationException {
        chunk=Class.forName(GAME+"ClientboundLevelChunkWithLightPacket");
        block=Class.forName(GAME+"ClientboundBlockUpdatePacket");
        section=Class.forName(GAME+"ClientboundSectionBlocksUpdatePacket");
        forget=Class.forName(GAME+"ClientboundForgetLevelChunkPacket");
        respawn=Class.forName(GAME+"ClientboundRespawnPacket");
        login=Class.forName(GAME+"ClientboundLoginPacket");
        blockEvent=Class.forName(GAME+"ClientboundBlockEventPacket");
        blockEventPos=objectMethod(blockEvent,"getPos");
        chunkX=method(chunk,"getX",int.class);chunkZ=method(chunk,"getZ",int.class);
        chunkReady=method(chunk,"isReady",boolean.class);chunkData=objectMethod(chunk,"getChunkData");
        chunkBuffer=privateField(Class.forName(GAME+"ClientboundLevelChunkPacketData"),"buffer",byte[].class);
        blockPos=objectMethod(block,"getPos");blockState=objectMethod(block,"getBlockState");
        Class<?> position=Class.forName("net.minecraft.core.BlockPos");
        posX=method(position,"getX",int.class);posY=method(position,"getY",int.class);posZ=method(position,"getZ",int.class);
        Class<?> state=Class.forName("net.minecraft.world.level.block.state.BlockState");
        stateId=LOOKUP.unreflect(Class.forName("net.minecraft.world.level.block.Block").getMethod("getId",state))
                .asType(MethodType.methodType(int.class,Object.class));
        sectionPos=privateField(section,"sectionPos",Object.class);
        sectionPositions=privateField(section,"positions",short[].class);
        sectionStates=privateField(section,"states",Object[].class);
        Class<?> sectionPosition=Class.forName("net.minecraft.core.SectionPos");
        relativeX=method(sectionPosition,"relativeToBlockX",int.class,short.class);
        relativeY=method(sectionPosition,"relativeToBlockY",int.class,short.class);
        relativeZ=method(sectionPosition,"relativeToBlockZ",int.class,short.class);
        forgetPos=objectMethod(forget,"pos");
        Class<?> chunkPosition=Class.forName("net.minecraft.world.level.ChunkPos");
        forgetX=method(chunkPosition,"x",int.class);forgetZ=method(chunkPosition,"z",int.class);
    }

    /** Budget runs before allocation/copy. A denied or unrepresentable update invalidates knowledge. */
    WorldSnapshot.Update read(Object packet,IntPredicate copyBudget) {
        return read(packet,copyBudget,ALL_CHUNKS);
    }
    WorldSnapshot.Update read(Object packet,IntPredicate copyBudget,ChunkInterest interest) {
        try {
            Class<?> type=packet.getClass();
            if(type==chunk) {
                int x=(int)chunkX.invokeExact(packet),z=(int)chunkZ.invokeExact(packet);
                if(!interest.test(x,z)) return null;
                if(!(boolean)chunkReady.invokeExact(packet)) return new WorldSnapshot.Forget(x,z);
                Object data=chunkData.invokeExact(packet);
                byte[] source=(byte[])chunkBuffer.invokeExact(data);
                if(source.length==0||source.length>ChunkCodec.MAX_BYTES||!copyBudget.test(source.length+48))
                    return new WorldSnapshot.Forget(x,z);
                return WorldSnapshot.EncodedChunk.owned(x,z,source.clone());
            }
            if(type==block) {
                Object position=blockPos.invokeExact(packet);
                int x=(int)posX.invokeExact(position),z=(int)posZ.invokeExact(position);
                if(!interest.test(x>>4,z>>4)) return null;
                if(!copyBudget.test(48)) return new WorldSnapshot.Forget(x>>4,z>>4);
                Object state=blockState.invokeExact(packet);
                return WorldSnapshot.Blocks.owned(new int[]{x,(int)posY.invokeExact(position),z,(int)stateId.invokeExact(state)});
            }
            if(type==section) {
                Object origin=sectionPos.invokeExact(packet);
                int x=(int)relativeX.invokeExact(origin,(short)0)>>4,
                        z=(int)relativeZ.invokeExact(origin,(short)0)>>4;
                if(!interest.test(x,z)) return null;
                short[] positions=(short[])sectionPositions.invokeExact(packet);
                Object[] states=(Object[])sectionStates.invokeExact(packet);
                if(positions.length!=states.length||positions.length>4096) return new WorldSnapshot.Invalidation();
                if(!copyBudget.test(32+16*positions.length))
                    return new WorldSnapshot.Forget(x,z);
                int[] entries=new int[positions.length*4];
                for(int i=0;i<positions.length;i++) {
                    short relative=positions[i];
                    if((relative&0xf000)!=0) return new WorldSnapshot.Invalidation();
                    entries[i*4]=(int)relativeX.invokeExact(origin,relative);
                    entries[i*4+1]=(int)relativeY.invokeExact(origin,relative);
                    entries[i*4+2]=(int)relativeZ.invokeExact(origin,relative);
                    entries[i*4+3]=(int)stateId.invokeExact(states[i]);
                }
                return WorldSnapshot.Blocks.owned(entries);
            }
            if(type==blockEvent) {
                Object position=blockEventPos.invokeExact(packet);
                int x=(int)posX.invokeExact(position)>>4,z=(int)posZ.invokeExact(position)>>4;
                // Any block event may affect neighboring chunks (notably piston pushes).
                // We do not replay block events: all interested events invalidate the replica.
                return interest.test(x,z)?new WorldSnapshot.Invalidation():null;
            }
            if(type==forget) {
                Object position=forgetPos.invokeExact(packet);
                return new WorldSnapshot.Forget((int)forgetX.invokeExact(position),(int)forgetZ.invokeExact(position));
            }
            if(type==respawn||type==login) return new WorldSnapshot.Reset();
            return null;
        } catch(RuntimeException failure) { throw failure; }
        catch(Throwable failure) { throw new IllegalStateException("Cannot capture 26.2 outbound world packet",failure); }
    }
    private static MethodHandle objectMethod(Class<?> owner,String name) throws ReflectiveOperationException {
        return LOOKUP.unreflect(owner.getMethod(name)).asType(MethodType.methodType(Object.class,Object.class));
    }
    private static MethodHandle method(Class<?> owner,String name,Class<?> result,Class<?>... parameters)
            throws ReflectiveOperationException {
        Class<?>[] signature=new Class<?>[parameters.length+1];signature[0]=Object.class;
        System.arraycopy(parameters,0,signature,1,parameters.length);
        return LOOKUP.unreflect(owner.getMethod(name,parameters)).asType(MethodType.methodType(result,signature));
    }
    private static MethodHandle privateField(Class<?> owner,String name,Class<?> result)
            throws ReflectiveOperationException {
        Field field=owner.getDeclaredField(name);field.setAccessible(true);
        return MethodHandles.lookup().unreflectGetter(field).asType(MethodType.methodType(result,Object.class));
    }
}
