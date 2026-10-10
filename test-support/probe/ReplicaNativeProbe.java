package dev.nordfjell.guard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.BitSet;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated runtime codec checks. Reads only the synthetic player's owned loaded chunk. */
final class ReplicaNativeProbe {
    private static int editX,editY,editZ,originalId,editedId;
    private ReplicaNativeProbe() {}

    static void edit(JavaPlugin guard,Player player,boolean restore) throws Exception {
        ClassLoader loader=guard.getClass().getClassLoader();
        Object level=player.getWorld().getClass().getMethod("getHandle").invoke(player.getWorld());
        Class<?> posType=type(loader,"net.minecraft.core.BlockPos");
        if(!restore) {
            var at=player.getLocation();
            // The fixture resends only the player's chunk; +2 can cross its edge at a random spawn.
            editX=(at.getBlockX()>>4)*16+8;editY=at.getBlockY()+3;editZ=(at.getBlockZ()>>4)*16+8;
        }
        Object pos=posType.getConstructor(int.class,int.class,int.class).newInstance(editX,editY,editZ);
        Object original=level.getClass().getMethod("getBlockState",posType).invoke(level,pos);
        Class<?> stateType=type(loader,"net.minecraft.world.level.block.state.BlockState");
        Method getId=type(loader,"net.minecraft.world.level.block.Block").getMethod("getId",stateType);
        Object block=type(loader,"net.minecraft.world.level.block.Blocks").getField("STONE").get(null);
        Object changed=block.getClass().getMethod("defaultBlockState").invoke(block);
        if(!restore) {originalId=(int)getId.invoke(null,original);editedId=(int)getId.invoke(null,changed);
            if(originalId==editedId)throw new AssertionError("Fixture edit must change a block");}
        Object packet=type(loader,"net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket")
                .getConstructor(posType,stateType).newInstance(pos,restore?original:changed);
        Object nativePlayer=player.getClass().getMethod("getHandle").invoke(player);
        Object connection=nativePlayer.getClass().getField("connection").get(nativePlayer);
        connection.getClass().getMethod("send",type(loader,"net.minecraft.network.protocol.Packet")).invoke(connection,packet);
    }

    static void verifyEdit(JavaPlugin guard,Player player,String phase) throws Exception {
        Field sessions=guard.getClass().getDeclaredField("sessions");sessions.setAccessible(true);
        Object session=((java.util.Map<?,?>)sessions.get(guard)).get(player.getUniqueId());
        Field syncField=session.getClass().getDeclaredField("worldSync");syncField.setAccessible(true);Object sync=syncField.get(session);
        int value=(int)method(sync.getClass(),"stateId",int.class,int.class,int.class).invoke(sync,editX,editY,editZ);
        if(phase.equals("pending")) {
            Field rawField=session.getClass().getDeclaredField("clientWorld");rawField.setAccessible(true);Object raw=rawField.get(session);
            int old=(int)method(raw.getClass(),"stateId",int.class,int.class,int.class,int.class)
                    .invoke(raw,editX,editY,editZ,player.getWorld().getMinHeight()>>4);
            if(old!=originalId || value!=-1 || (int)method(sync.getClass(),"pending").invoke(sync)<1)
                throw new AssertionError("Unacknowledged edit became known: raw="+old+", safe="+value);
        } else {
            int expected=phase.equals("restored")?originalId:editedId;
            if(value!=expected)throw new AssertionError("Acknowledged edit expected="+expected+", actual="+value);
        }
    }

    /** Sends one fresh ordinary chunk packet through the real outbound observer. */
    static void resend(JavaPlugin guard,Player player) throws Exception {
        if(!Bukkit.isOwnedByCurrentRegion(player)) throw new AssertionError("Resend requires player owner thread");
        ClassLoader loader=guard.getClass().getClassLoader();
        Object packet=freshChunkPacket(loader,player);
        Object nativePlayer=player.getClass().getMethod("getHandle").invoke(player);
        Object connection=nativePlayer.getClass().getField("connection").get(nativePlayer);
        connection.getClass().getMethod("send",type(loader,"net.minecraft.network.protocol.Packet")).invoke(connection,packet);
    }

    /** Call later on the entity scheduler, after the real inbox has consumed the resend. */
    static void verifyCaptured(JavaPlugin guard,Player player,Consumer<String> pass) throws Exception {
        if(!Bukkit.isOwnedByCurrentRegion(player)) throw new AssertionError("Capture verification requires player owner thread");
        Field sessionsField=guard.getClass().getDeclaredField("sessions");sessionsField.setAccessible(true);
        Object session=((java.util.Map<?,?>)sessionsField.get(guard)).get(player.getUniqueId());
        if(session==null) throw new AssertionError("Guard player session missing");
        Field worldField=session.getClass().getDeclaredField("clientWorld");worldField.setAccessible(true);
        Object replica=worldField.get(session);
        if(replica==null||(long)method(replica.getClass(),"decoded").invoke(replica)<=0)
            throw new AssertionError("Actual outbound chunk was not decoded");
        Method replicaState=method(replica.getClass(),"stateId",int.class,int.class,int.class,int.class);
        ClassLoader loader=guard.getClass().getClassLoader();
        Class<?> state=type(loader,"net.minecraft.world.level.block.state.BlockState");
        Method stateId=type(loader,"net.minecraft.world.level.block.Block").getMethod("getId",state);
        Object level=player.getWorld().getClass().getMethod("getHandle").invoke(player.getWorld());
        var position=player.getLocation();
        int x=position.getBlockX(),y=position.getBlockY(),z=position.getBlockZ();
        Object nativeChunk=level.getClass().getMethod("getChunkIfLoaded",int.class,int.class).invoke(level,x>>4,z>>4);
        if(nativeChunk==null) throw new AssertionError("Owned capture chunk unloaded");
        Method nativeState=nativeChunk.getClass().getMethod("getBlockState",int.class,int.class,int.class);
        int minSection=player.getWorld().getMinHeight()>>4;
        for(int dy:new int[]{-1,0,1}) {
            int expected=(int)stateId.invoke(null,nativeState.invoke(nativeChunk,x,y+dy,z));
            assertState(replicaState,replica,x,y+dy,z,minSection,expected);
        }
        Field syncField=session.getClass().getDeclaredField("worldSync");syncField.setAccessible(true);Object sync=syncField.get(session);
        Method safe=method(sync.getClass(),"stateId",int.class,int.class,int.class);
        for(int dy:new int[]{-1,0,1}) {
            int expected=(int)stateId.invoke(null,nativeState.invoke(nativeChunk,x,y+dy,z));
            if((int)safe.invoke(sync,x,y+dy,z)!=expected)throw new AssertionError("Actual chunk prefix not acknowledged");
        }
        pass.accept("replica_actual_pipeline_queue_captured_foot_and_body");
    }

    private static Object freshChunkPacket(ClassLoader loader,Player player) throws Exception {
        var position=player.getLocation();
        Object level=player.getWorld().getClass().getMethod("getHandle").invoke(player.getWorld());
        Object chunk=level.getClass().getMethod("getChunkIfLoaded",int.class,int.class)
                .invoke(level,position.getBlockX()>>4,position.getBlockZ()>>4);
        if(chunk==null) throw new AssertionError("Owned resend chunk must already be loaded");
        Object source=level.getClass().getMethod("getChunkSource").invoke(level);
        Object light=source.getClass().getMethod("getLightEngine").invoke(source);
        Class<?> packetType=type(loader,"net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket");
        Object packet=packetType.getConstructor(type(loader,"net.minecraft.world.level.chunk.LevelChunk"),
                type(loader,"net.minecraft.world.level.lighting.LevelLightEngine"),BitSet.class,BitSet.class,boolean.class)
                .newInstance(chunk,light,null,null,false);
        if(!(boolean)packetType.getMethod("isReady").invoke(packet))
            throw new AssertionError("Unobfuscated test chunk unexpectedly asynchronous");
        return packet;
    }

    static void run(JavaPlugin guard,Player player,Consumer<String> pass) throws Exception {
        if(!Bukkit.isOwnedByCurrentRegion(player)) throw new AssertionError("Replica probe requires player owner thread");
        ClassLoader loader=guard.getClass().getClassLoader();
        int cx=player.getLocation().getBlockX()>>4,cz=player.getLocation().getBlockZ()>>4;
        int minSection=player.getWorld().getMinHeight()>>4;
        int expectedSections=(player.getWorld().getMaxHeight()-player.getWorld().getMinHeight())>>4;
        Object level=player.getWorld().getClass().getMethod("getHandle").invoke(player.getWorld());
        Object nativeChunk=level.getClass().getMethod("getChunkIfLoaded",int.class,int.class).invoke(level,cx,cz);
        if(nativeChunk==null) throw new AssertionError("Synthetic player's chunk must already be loaded");
        Object source=level.getClass().getMethod("getChunkSource").invoke(level);
        Object light=source.getClass().getMethod("getLightEngine").invoke(source);
        Class<?> chunkPacket=type(loader,"net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket");
        Object packet=chunkPacket.getConstructor(type(loader,"net.minecraft.world.level.chunk.LevelChunk"),
                type(loader,"net.minecraft.world.level.lighting.LevelLightEngine"),BitSet.class,BitSet.class,boolean.class)
                .newInstance(nativeChunk,light,null,null,false);
        if(!(boolean)chunkPacket.getMethod("isReady").invoke(packet))
            throw new AssertionError("Unobfuscated synthetic chunk packet unexpectedly asynchronous");

        Class<?> adapterType=type(loader,"dev.nordfjell.guard.NativeWorld");
        Object adapter=method(adapterType,"bind").invoke(null);
        Method read=method(adapterType,"read",Object.class,IntPredicate.class);
        IntPredicate allow=bytes->true,deny=bytes->false;
        Object nativePlayer=player.getClass().getMethod("getHandle").invoke(player);
        Object info=nativePlayer.getClass().getMethod("createCommonSpawnInfo",type(loader,"net.minecraft.server.level.ServerLevel"))
                .invoke(nativePlayer,level);
        Object respawn=type(loader,"net.minecraft.network.protocol.game.ClientboundRespawnPacket")
                .getConstructor(type(loader,"net.minecraft.network.protocol.game.CommonPlayerSpawnInfo"),byte.class).newInstance(info,(byte)0);
        Object reset=read.invoke(adapter,respawn,allow);requireType(reset,"Reset");
        assertInt(reset,"minSection",minSection);assertInt(reset,"sections",expectedSections);
        if(!method(reset.getClass(),"dimension").invoke(reset).equals(player.getWorld().getKey().toString()))
            throw new AssertionError("Native dimension key mismatch");
        pass.accept("replica_native_dimension_metadata");
        Object encoded=read.invoke(adapter,packet,allow);
        requireType(encoded,"EncodedChunk");
        Object decoded=method(encoded.getClass(),"decode").invoke(encoded);
        assertInt(decoded,"sectionCount",expectedSections);
        assertInt(decoded,"x",cx);assertInt(decoded,"z",cz);
        Class<?> blockType=type(loader,"net.minecraft.world.level.block.Block");
        Class<?> stateType=type(loader,"net.minecraft.world.level.block.state.BlockState");
        Method stateId=blockType.getMethod("getId",stateType);
        Method nativeState=nativeChunk.getClass().getMethod("getBlockState",int.class,int.class,int.class);
        Method section=method(decoded.getClass(),"section",int.class);
        int samples=0;
        for(int sy=0;sy<expectedSections;sy++) {
            Object palette=section.invoke(decoded,sy);
            Method localState=method(palette.getClass(),"stateId",int.class,int.class,int.class);
            // 64 samples in every section, covering negative world Y and palette word boundaries.
            for(int ly=0;ly<16;ly+=5) for(int lz=0;lz<16;lz+=5) for(int lx=0;lx<16;lx+=5) {
                int wx=(cx<<4)+lx,wy=((minSection+sy)<<4)+ly,wz=(cz<<4)+lz;
                int expected=(int)stateId.invoke(null,nativeState.invoke(nativeChunk,wx,wy,wz));
                int actual=(int)localState.invoke(palette,lx,ly,lz);
                if(expected!=actual) throw new AssertionError("Native palette mismatch at "+wx+","+wy+","+wz
                        +": expected="+expected+", actual="+actual);
                samples++;
            }
        }
        pass.accept("replica_native_chunk_sections_"+expectedSections+"_samples_"+samples);

        Class<?> worldType=type(loader,"dev.nordfjell.guard.ClientWorld");
        Constructor<?> worldConstructor=worldType.getDeclaredConstructor();worldConstructor.setAccessible(true);
        Object replica=worldConstructor.newInstance();
        Class<?> updateType=type(loader,"dev.nordfjell.guard.WorldSnapshot$Update");
        Method apply=method(worldType,"apply",updateType,int.class,int.class);
        Method replicaState=method(worldType,"stateId",int.class,int.class,int.class,int.class);
        apply.invoke(replica,encoded,expectedSections,minSection);
        int sy=Math.min(expectedSections-1,Math.max(0,-minSection+4));
        int x=(cx<<4)+3,y=((minSection+sy)<<4)+4,z=(cz<<4)+5;
        int expected=(int)stateId.invoke(null,nativeState.invoke(nativeChunk,x,y,z));
        assertState(replicaState,replica,x,y,z,minSection,expected);
        pass.accept("replica_native_chunk_apply");

        Class<?> blocks=type(loader,"net.minecraft.world.level.block.Blocks");
        Object air=blocks.getField("AIR").get(null),stone=blocks.getField("STONE").get(null);
        Object airState=air.getClass().getMethod("defaultBlockState").invoke(air);
        Object stoneState=stone.getClass().getMethod("defaultBlockState").invoke(stone);
        int stoneId=(int)stateId.invoke(null,stoneState),airId=(int)stateId.invoke(null,airState);
        Class<?> posType=type(loader,"net.minecraft.core.BlockPos");
        Object pos=posType.getConstructor(int.class,int.class,int.class).newInstance(x,y,z);
        Class<?> blockPacket=type(loader,"net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket");
        Object single=blockPacket.getConstructor(posType,stateType).newInstance(pos,stoneState);
        Object singleUpdate=read.invoke(adapter,single,allow);requireType(singleUpdate,"Blocks");
        assertInt(singleUpdate,"count",1);
        apply.invoke(replica,singleUpdate,expectedSections,minSection);
        assertState(replicaState,replica,x,y,z,minSection,stoneId);
        pass.accept("replica_native_single_block");

        Class<?> sectionPos=type(loader,"net.minecraft.core.SectionPos");
        Object nativeSection=sectionPos.getMethod("of",int.class,int.class,int.class).invoke(null,cx,minSection+sy,cz);
        Class<?> shortMap=type(loader,"it.unimi.dsi.fastutil.shorts.Short2ObjectMap");
        Class<?> mapType=type(loader,"it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap");
        Object map=mapType.getConstructor().newInstance();
        Method put=mapType.getMethod("put",short.class,Object.class);
        put.invoke(map,(short)((2<<8)|(7<<4)|6),stoneState);
        put.invoke(map,(short)((14<<8)|(1<<4)|9),airState);
        Object batch=type(loader,"net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket")
                .getConstructor(sectionPos,shortMap).newInstance(nativeSection,map);
        Object batchUpdate=read.invoke(adapter,batch,allow);requireType(batchUpdate,"Blocks");assertInt(batchUpdate,"count",2);
        apply.invoke(replica,batchUpdate,expectedSections,minSection);
        assertState(replicaState,replica,(cx<<4)+2,((minSection+sy)<<4)+6,(cz<<4)+7,minSection,stoneId);
        assertState(replicaState,replica,(cx<<4)+14,((minSection+sy)<<4)+9,(cz<<4)+1,minSection,airId);
        pass.accept("replica_native_section_coordinates_and_states");

        assertForget(read.invoke(adapter,packet,deny),cx,cz);
        assertForget(read.invoke(adapter,single,deny),cx,cz);
        assertForget(read.invoke(adapter,batch,deny),cx,cz);
        chunkPacket.getMethod("setReady",boolean.class).invoke(packet,false);
        try {assertForget(read.invoke(adapter,packet,allow),cx,cz);}
        finally {chunkPacket.getMethod("setReady",boolean.class).invoke(packet,true);}
        pass.accept("replica_native_denied_budget_and_not_ready");

        Class<?> interestType=type(loader,"dev.nordfjell.guard.NativeWorld$ChunkInterest");
        Object noInterest=Proxy.newProxyInstance(loader,new Class<?>[]{interestType},(proxy,m,args)->false);
        Method interestedRead=method(adapterType,"read",Object.class,IntPredicate.class,interestType);
        IntPredicate mustNotReserve=bytes->{throw new AssertionError("Far packet reserved copy budget");};
        for(Object far:new Object[]{packet,single,batch})
            if(interestedRead.invoke(adapter,far,mustNotReserve,noInterest)!=null)
                throw new AssertionError("Far update was not ignored");
        pass.accept("replica_native_far_interest_before_budget");

        Object event=type(loader,"net.minecraft.network.protocol.game.ClientboundBlockEventPacket")
                .getConstructor(posType,blockType,int.class,int.class).newInstance(pos,stone,0,0);
        requireType(read.invoke(adapter,event,allow),"Invalidation");
        requireType(interestedRead.invoke(adapter,event,mustNotReserve,noInterest),"Invalidation");
        pass.accept("replica_native_block_events_invalidate");

        Class<?> chunkPos=type(loader,"net.minecraft.world.level.ChunkPos");
        Object nativeChunkPos=chunkPos.getConstructor(int.class,int.class).newInstance(cx,cz);
        Object forget=type(loader,"net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket")
                .getConstructor(chunkPos).newInstance(nativeChunkPos);
        Object forgotten=read.invoke(adapter,forget,allow);assertForget(forgotten,cx,cz);
        apply.invoke(replica,forgotten,expectedSections,minSection);
        assertState(replicaState,replica,x,y,z,minSection,-1);
        pass.accept("replica_native_forget_is_unknown_not_air");

        Field geometryField=guard.getClass().getDeclaredField("blockGeometry");geometryField.setAccessible(true);
        Object geometry=geometryField.get(guard);
        if(geometry==null||(int)method(geometry.getClass(),"supported").invoke(geometry)<=0)
            throw new AssertionError("Native block geometry was not extracted");
        Object airGeometry=method(geometry.getClass(),"state",int.class).invoke(geometry,airId);
        if(!(boolean)method(airGeometry.getClass(),"supported").invoke(airGeometry))
            throw new AssertionError("Native air geometry unsupported");
        if(((Object[])method(airGeometry.getClass(),"shapes").invoke(airGeometry)).length!=0)
            throw new AssertionError("Native air has collision boxes");
        pass.accept("replica_native_geometry_registry");
    }
    private static Class<?> type(ClassLoader loader,String name) throws ClassNotFoundException {
        return Class.forName(name,true,loader);
    }
    private static Method method(Class<?> owner,String name,Class<?>... args) throws Exception {
        Method result=owner.getDeclaredMethod(name,args);result.setAccessible(true);return result;
    }
    private static void requireType(Object value,String name) {
        if(value==null||!value.getClass().getSimpleName().equals(name))
            throw new AssertionError("Expected "+name+", got "+(value==null?"null":value.getClass().getName()));
    }
    private static void assertInt(Object object,String getter,int expected) throws Exception {
        int actual=(int)method(object.getClass(),getter).invoke(object);
        if(actual!=expected) throw new AssertionError(getter+": expected="+expected+", actual="+actual);
    }
    private static void assertForget(Object update,int x,int z) throws Exception {
        requireType(update,"Forget");assertInt(update,"x",x);assertInt(update,"z",z);
    }
    private static void assertState(Method getter,Object replica,int x,int y,int z,int minSection,int expected) throws Exception {
        int actual=(int)getter.invoke(replica,x,y,z,minSection);
        if(actual!=expected) throw new AssertionError("Replica state at "+x+","+y+","+z+": expected="+expected+", actual="+actual);
    }
}
