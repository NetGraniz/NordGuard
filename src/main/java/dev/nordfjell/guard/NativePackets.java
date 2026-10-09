package dev.nordfjell.guard;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Version-pinned observation only. No Bukkit state is read by a channel callback. */
final class NativePackets {
    static final int MOVE=1, TICK_END=2, INPUT=3, TELEPORT=4, TELEPORT_ACK=5,
            VELOCITY=6, PONG=7, WORLD_CHANGE=8, CONTEXT_CHANGE=9, CLOSED=10,
            BARRIER_SENT=11, ATTACHED=12, WORLD_DATA=13;
    private static final String GAME="net.minecraft.network.protocol.game.";
    private static final String COMMON="net.minecraft.network.protocol.common.";
    private static final MethodHandles.Lookup LOOKUP=MethodHandles.publicLookup();
    @FunctionalInterface interface Sink {
        void event(int kind,long nano,int id,int flags,double x,double y,double z,float yaw,float pitch);
        default boolean reserveWorldCopy(int bytes) { return false; }
        default boolean worldEnabled() { return false; }
        default void world(WorldSnapshot.Update update) {}
    }
    private final NativeWorld world;
    private final Class<?> move, tickEnd, input, teleport, teleportAck, velocity, pong, bundle, ping;
    private final MethodHandle handle, listenerConnection, networkConnection, channel;
    private final MethodHandle hasPos, hasRot, onGround, collision, x, y, z, yaw, pitch;
    private final MethodHandle inputValue;
    private final MethodHandle[] inputBits=new MethodHandle[7];
    private final MethodHandle teleportId, teleportChange, teleportRelatives, position, rotationY, rotationX;
    private final MethodHandle ackId, velocityId, velocityValue, pongId, subPackets, pingConstructor, pingId;
    private final MethodHandle vecX, vecY, vecZ;
    private final Set<Class<?>> worldChanges=new HashSet<>(), contextChanges=new HashSet<>();
    private final Map<Class<?>,MethodHandle> selfContext=new HashMap<>();

    static NativePackets bind() throws ReflectiveOperationException {
        if(!Bukkit.getMinecraftVersion().equals("26.2"))
            throw new ReflectiveOperationException("Packet observer supports Minecraft 26.2 only");
        return new NativePackets();
    }
    private NativePackets() throws ReflectiveOperationException {
        world=NativeWorld.bind();
        Class<?> craft=type("org.bukkit.craftbukkit.entity.CraftPlayer");
        Class<?> serverPlayer=type("net.minecraft.server.level.ServerPlayer");
        Class<?> listener=type("net.minecraft.server.network.ServerCommonPacketListenerImpl");
        Class<?> connection=type("net.minecraft.network.Connection");
        handle=objectMethod(craft,"getHandle");
        listenerConnection=field(serverPlayer,"connection");
        networkConnection=field(listener,"connection");
        channel=field(connection,"channel");
        move=type(GAME+"ServerboundMovePlayerPacket");
        tickEnd=type(GAME+"ServerboundClientTickEndPacket");
        input=type(GAME+"ServerboundPlayerInputPacket");
        teleport=type(GAME+"ClientboundPlayerPositionPacket");
        teleportAck=type(GAME+"ServerboundAcceptTeleportationPacket");
        velocity=type(GAME+"ClientboundSetEntityMotionPacket");
        pong=type(COMMON+"ServerboundPongPacket");
        bundle=type("net.minecraft.network.protocol.BundlePacket");
        hasPos=primitiveMethod(move,"hasPosition",boolean.class);
        hasRot=primitiveMethod(move,"hasRotation",boolean.class);
        onGround=primitiveMethod(move,"isOnGround",boolean.class);
        collision=primitiveMethod(move,"horizontalCollision",boolean.class);
        x=primitiveMethod(move,"getX",double.class,double.class);
        y=primitiveMethod(move,"getY",double.class,double.class);
        z=primitiveMethod(move,"getZ",double.class,double.class);
        yaw=primitiveMethod(move,"getYRot",float.class,float.class);
        pitch=primitiveMethod(move,"getXRot",float.class,float.class);
        inputValue=objectMethod(input,"input");
        Class<?> keys=type("net.minecraft.world.entity.player.Input");
        String[] names={"forward","backward","left","right","jump","shift","sprint"};
        for(int i=0;i<names.length;i++) inputBits[i]=primitiveMethod(keys,names[i],boolean.class);
        teleportId=primitiveMethod(teleport,"id",int.class);
        teleportChange=objectMethod(teleport,"change");
        teleportRelatives=objectMethod(teleport,"relatives");
        Class<?> change=type("net.minecraft.world.entity.PositionMoveRotation");
        position=objectMethod(change,"position");
        rotationY=primitiveMethod(change,"yRot",float.class);
        rotationX=primitiveMethod(change,"xRot",float.class);
        ackId=primitiveMethod(teleportAck,"getId",int.class);
        velocityId=primitiveMethod(velocity,"id",int.class);
        velocityValue=objectMethod(velocity,"movement");
        pongId=primitiveMethod(pong,"getId",int.class);
        subPackets=objectMethod(bundle,"subPackets");
        ping=type(COMMON+"ClientboundPingPacket");
        pingId=primitiveMethod(ping,"getId",int.class);
        pingConstructor=LOOKUP.unreflectConstructor(ping.getConstructor(int.class))
                .asType(MethodType.methodType(Object.class,int.class));
        Class<?> vec=type("net.minecraft.world.phys.Vec3");
        vecX=primitiveMethod(vec,"x",double.class);
        vecY=primitiveMethod(vec,"y",double.class);
        vecZ=primitiveMethod(vec,"z",double.class);
        for(String name:new String[]{"ClientboundBlockUpdatePacket","ClientboundSectionBlocksUpdatePacket",
                "ClientboundLevelChunkWithLightPacket","ClientboundForgetLevelChunkPacket",
                "ClientboundBlockEventPacket","ClientboundRespawnPacket","ClientboundLoginPacket"})
            worldChanges.add(type(GAME+name));
        for(String name:new String[]{"ClientboundPlayerAbilitiesPacket","ClientboundExplodePacket",
                "ClientboundPlayerRotationPacket","ClientboundStartConfigurationPacket"})
            contextChanges.add(type(GAME+name));
        self("ClientboundUpdateAttributesPacket","getEntityId");
        self("ClientboundUpdateMobEffectPacket","getEntityId");
        self("ClientboundRemoveMobEffectPacket","entityId");
        self("ClientboundSetEntityDataPacket","id");
    }
    private void self(String name,String getter) throws ReflectiveOperationException {
        Class<?> c=type(GAME+name);selfContext.put(c,primitiveMethod(c,getter,int.class));
    }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }
    private static MethodHandle objectMethod(Class<?> owner,String name) throws ReflectiveOperationException {
        return LOOKUP.unreflect(owner.getMethod(name)).asType(MethodType.methodType(Object.class,Object.class));
    }
    private static MethodHandle field(Class<?> owner,String name) throws ReflectiveOperationException {
        return LOOKUP.unreflectGetter(owner.getField(name)).asType(MethodType.methodType(Object.class,Object.class));
    }
    private static MethodHandle primitiveMethod(Class<?> owner,String name,Class<?> result,Class<?>... args)
            throws ReflectiveOperationException {
        Class<?>[] adapted=new Class<?>[args.length+1];adapted[0]=Object.class;
        System.arraycopy(args,0,adapted,1,args.length);
        return LOOKUP.unreflect(owner.getMethod(name,args)).asType(MethodType.methodType(result,adapted));
    }

    Handle attach(Player player,Sink sink) throws ReflectiveOperationException {
        if(!Bukkit.isOwnedByCurrentRegion(player)) throw new IllegalStateException("Attach must run on player's owner thread");
        Objects.requireNonNull(sink);
        try {
            Object nativePlayer=handle.invokeExact((Object)player);
            Object listener=listenerConnection.invokeExact(nativePlayer);
            Object connection=networkConnection.invokeExact(listener);
            Channel transport=(Channel)(Object)channel.invokeExact(connection);
            var at=player.getLocation();
            Handle result=new Handle(transport,player.getEntityId(),sink,at.getX(),at.getZ());
            transport.eventLoop().execute(result::install);
            return result;
        } catch(Throwable failure) { throw new ReflectiveOperationException("Cannot attach 26.2 packet observer",failure); }
    }
    void probe(Handle target,int id) {
        if(target.closing) return;
        try { target.transport.eventLoop().execute(()->target.sendProbe(id)); }
        catch(RejectedExecutionException closedLoop) { target.closing=true; }
    }

    final class Handle implements AutoCloseable {
        private final Channel transport;
        private final int entityId;
        private final Sink sink;
        private final java.util.function.IntPredicate copyBudget;
        private final NativeWorld.ChunkInterest chunkInterest;
        private final boolean worldEnabled;
        private double positionX,positionZ;
        private int centerX,centerZ;
        private final String name="nordguard_observer";
        private final Observer observer=new Observer(this);
        private volatile boolean closing;
        private volatile boolean failed,installed;
        private Object ownPing;
        private final int[] foreignPingIds = new int[8];
        private int foreignPingCount, foreignPingCursor;
        private boolean closedReported;
        Handle(Channel transport,int entityId,Sink sink,double x,double z) {
            this.transport=transport;this.entityId=entityId;this.sink=sink;this.copyBudget=sink::reserveWorldCopy;
            worldEnabled=sink.worldEnabled();
            positionX=x;positionZ=z;centerX=((int)Math.floor(x))>>4;centerZ=((int)Math.floor(z))>>4;
            chunkInterest=(cx,cz)->Math.abs((long)cx-centerX)<=1 && Math.abs((long)cz-centerZ)<=1;
        }
        private void center(double x,double z) {
            if(!Double.isFinite(x)||!Double.isFinite(z)||Math.abs(x)>32_000_000||Math.abs(z)>32_000_000)return;
            int cx=((int)Math.floor(x))>>4,cz=((int)Math.floor(z))>>4;
            positionX=x;positionZ=z;
            if(cx!=centerX||cz!=centerZ) {centerX=cx;centerZ=cz;if(worldEnabled)sink.world(new WorldSnapshot.Retain(cx,cz));}
        }
        boolean active() { return installed && !closing && !failed; }
        private void emit(int kind,int id,int flags,double x,double y,double z,float yaw,float pitch) {
            sink.event(kind,System.nanoTime(),id,flags,x,y,z,yaw,pitch);
        }
        private void marker(int kind,int id) { emit(kind,id,0,0,0,0,0,0); }
        private void failed() {
            failed=true;
            if(!closedReported) {
                closedReported=true;
                try { marker(CLOSED,0); } catch(Throwable ignored) { /* Observation must not break networking. */ }
            }
        }
        private void install() {
            if(closing || !transport.isActive() || transport.pipeline().get("packet_handler")==null) { failed();return; }
            try {
                // Never replace another instance: its lifecycle may still be completing.
                if(transport.pipeline().get(name)!=null) { failed();return; }
                transport.pipeline().addBefore("packet_handler",name,observer);
                installed=true;marker(ATTACHED,entityId);
            } catch(Throwable failure) { failed(); }
        }
        private void sendProbe(int id) {
            if(closing || failed || !installed || !transport.isActive()
                    || transport.pipeline().get(name)!=observer) { if(!closing) failed();return; }
            try {
                for (int i=0; i<foreignPingCount; i++) if(foreignPingIds[i]==id) return;
                Object packet=pingConstructor.invokeExact(id);
                marker(BARRIER_SENT,id);
                ownPing=packet;
                try { transport.writeAndFlush(packet).addListener(future->{ if(!future.isSuccess()) failed(); }); }
                finally { ownPing=null; }
            } catch(Throwable failure) { failed(); }
        }
        @Override public void close() {
            if(closing) return;
            closing=true;
            try { transport.eventLoop().execute(()->{
                if(transport.pipeline().get(name)==observer) transport.pipeline().remove(observer);
                installed=false;failed();
            }); } catch(RejectedExecutionException closedLoop) { /* No callbacks remain on a terminated event loop. */ }
        }
    }
    private final class Observer extends ChannelDuplexHandler {
        private final Handle owner;
        Observer(Handle owner) { this.owner=owner; }
        @Override public void channelRead(ChannelHandlerContext ctx,Object message) throws Exception {
            if(!owner.failed && !owner.closing) {
                try { inbound(owner,message); } catch(Throwable failure) { owner.failed(); }
            }
            ctx.fireChannelRead(message);
        }
        @Override public void write(ChannelHandlerContext ctx,Object message,ChannelPromise promise) throws Exception {
            if(!owner.failed && !owner.closing) {
                try { outbound(owner,message); } catch(Throwable failure) { owner.failed(); }
            }
            ctx.write(message,promise);
        }
        @Override public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            owner.failed();ctx.fireChannelInactive();
        }
        @Override public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            owner.installed=false;owner.failed();
        }
    }
    private void inbound(Handle h,Object packet) throws Throwable {
        if(move.isInstance(packet)) {
            int flags=((boolean)hasPos.invokeExact(packet)?1:0)|((boolean)hasRot.invokeExact(packet)?2:0)
                    |((boolean)onGround.invokeExact(packet)?4:0)|((boolean)collision.invokeExact(packet)?8:0);
            double px=(double)x.invokeExact(packet,Double.NaN),pz=(double)z.invokeExact(packet,Double.NaN);
            if((flags&1)!=0)h.center(px,pz);
            h.emit(MOVE,0,flags,px,(double)y.invokeExact(packet,Double.NaN),pz,
                    (float)yaw.invokeExact(packet,Float.NaN),(float)pitch.invokeExact(packet,Float.NaN));
        } else if(tickEnd.isInstance(packet)) h.marker(TICK_END,0);
        else if(input.isInstance(packet)) {
            Object keys=inputValue.invokeExact(packet);int flags=0;
            for(int i=0;i<inputBits.length;i++) if((boolean)inputBits[i].invokeExact(keys)) flags|=1<<i;
            h.emit(INPUT,0,flags,0,0,0,0,0);
        } else if(teleportAck.isInstance(packet)) h.marker(TELEPORT_ACK,(int)ackId.invokeExact(packet));
        else if(pong.isInstance(packet)) h.marker(PONG,(int)pongId.invokeExact(packet));
    }
    private void outbound(Handle h,Object packet) throws Throwable {
        if(bundle.isInstance(packet)) {
            Iterable<?> packets=(Iterable<?>)(Object)subPackets.invokeExact(packet);int count=0;
            for(Object child:packets) {
                if(++count>64 || bundle.isInstance(child)) {
                    if(h.worldEnabled)h.sink.world(new WorldSnapshot.Invalidation());
                    h.marker(WORLD_CHANGE,0);h.marker(CONTEXT_CHANGE,0);break;
                }
                outboundSingle(h,child);
            }
        } else outboundSingle(h,packet);
    }
    private void outboundSingle(Handle h,Object packet) throws Throwable {
        WorldSnapshot.Update update=h.worldEnabled?world.read(packet,h.copyBudget,h.chunkInterest):null;
        if(update!=null) {
            h.marker(WORLD_CHANGE,0);
            h.sink.world(update);
            return;
        }
        if(ping.isInstance(packet)) {
            if(packet!=h.ownPing) {
                int id=(int)pingId.invokeExact(packet);
                h.foreignPingIds[h.foreignPingCursor++ & 7]=id;
                h.foreignPingCount=Math.min(8,h.foreignPingCount+1);
                // No reserved ID namespace exists: abandon our outstanding measurements.
                h.marker(CONTEXT_CHANGE,0);
            }
        } else if(teleport.isInstance(packet)) {
            Object change=teleportChange.invokeExact(packet);
            Object pos=position.invokeExact(change);
            Set<?> relative=(Set<?>)(Object)teleportRelatives.invokeExact(packet);
            double px=(double)vecX.invokeExact(pos),pz=(double)vecZ.invokeExact(pos);
            boolean relativeX=false,relativeZ=false;
            for(Object flag:relative) {String name=((Enum<?>)flag).name();relativeX|=name.equals("X");relativeZ|=name.equals("Z");}
            h.center(relativeX?h.positionX+px:px,relativeZ?h.positionZ+pz:pz);
            h.emit(TELEPORT,(int)teleportId.invokeExact(packet),relative.isEmpty()?0:1,
                    px,(double)vecY.invokeExact(pos),pz,
                    (float)rotationY.invokeExact(change),(float)rotationX.invokeExact(change));
        } else if(velocity.isInstance(packet)) {
            int id=(int)velocityId.invokeExact(packet);
            if(id==h.entityId) {
                Object v=velocityValue.invokeExact(packet);
                h.emit(VELOCITY,id,0,(double)vecX.invokeExact(v),(double)vecY.invokeExact(v),(double)vecZ.invokeExact(v),0,0);
            }
        } else if(worldChanges.contains(packet.getClass())) h.marker(WORLD_CHANGE,0);
        else if(contextChanges.contains(packet.getClass())) h.marker(CONTEXT_CHANGE,0);
        else {
            MethodHandle getter=selfContext.get(packet.getClass());
            if(getter!=null && (int)getter.invokeExact(packet)==h.entityId) h.marker(CONTEXT_CHANGE,h.entityId);
        }
    }
}
