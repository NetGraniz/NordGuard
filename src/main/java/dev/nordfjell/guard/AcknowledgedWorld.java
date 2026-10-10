package dev.nordfjell.guard;

import java.util.Arrays;
import java.util.function.IntPredicate;

/** Entity-owner only. Pong confirms an observed outbound prefix, not client obedience. */
final class AcknowledgedWorld {
    static final int MAX_UPDATES=16, MAX_BYTES=512*1024, MAX_BARRIERS=4;
    private final ClientWorld confirmed;
    private final IntPredicate decodeBudget, materializeBudget;
    private final WorldSnapshot.Update[] updates=new WorldSnapshot.Update[MAX_UPDATES];
    private final long[] sequences=new long[MAX_UPDATES];
    private final int[] chunkX=new int[MAX_UPDATES],chunkZ=new int[MAX_UPDATES];
    private final int[] ids=new int[MAX_BARRIERS],recentIds=new int[16];
    private final long[] sent=new long[MAX_BARRIERS],targets=new long[MAX_BARRIERS];
    private int head,count,bytes,barriers,recentCount,recentCursor;
    private int minSection,sections,centerX,centerZ;
    private String dimension,reason="awaiting barrier";
    private long sequence,acknowledged,commits,resets,geometryRevision;
    private boolean prefixKnown;

    AcknowledgedWorld(ClientWorld confirmed,int minSection,int sections,String dimension,int centerX,int centerZ,
                      IntPredicate decodeBudget,IntPredicate materializeBudget) {
        this.confirmed=confirmed;this.decodeBudget=decodeBudget;this.materializeBudget=materializeBudget;
        this.centerX=centerX;this.centerZ=centerZ;
        setDimension(minSection,sections,dimension);
    }

    void stage(WorldSnapshot.Update update) {
        if(update instanceof WorldSnapshot.Reset reset) {
            invalidate("dimension reset");
            setDimension(reset.minSection(),reset.sections(),reset.dimension());return;
        }
        if(update instanceof WorldSnapshot.Invalidation) {invalidate("uncertain outbound data");return;}
        if(update instanceof WorldSnapshot.Retain retain) {
            geometryRevision++;
            centerX=retain.x();centerZ=retain.z();
            confirmed.apply(retain,sections,minSection);return;
        }
        if(sections==0) {invalidate("unknown dimension");return;}
        int x,z;
        if(update instanceof WorldSnapshot.EncodedChunk chunk) {x=chunk.x();z=chunk.z();}
        else if(update instanceof WorldSnapshot.Chunk chunk) {x=chunk.x();z=chunk.z();}
        else if(update instanceof WorldSnapshot.Forget forget) {x=forget.x();z=forget.z();}
        else if(update instanceof WorldSnapshot.Blocks blocks) {
            if(blocks.count()==0)return;
            x=blocks.x(0)>>4;z=blocks.z(0)>>4;
            // Native block and section updates always belong to one chunk. Reject malformed detached batches.
            for(int i=0;i<blocks.count();i++) if(blocks.x(i)>>4!=x || blocks.z(i)>>4!=z
                    || (blocks.y(i)>>4)<minSection || (blocks.y(i)>>4)>=minSection+sections) {
                invalidate("unsupported block batch");return;
            }
        } else {invalidate("unsupported world update");return;}
        if(!interested(x,z))return;
        if(count==MAX_UPDATES || update.estimatedBytes()>MAX_BYTES-bytes) {
            invalidate("world journal capacity");return;
        }
        if(update instanceof WorldSnapshot.EncodedChunk encoded) {
            if(!decodeBudget.test(encoded.estimatedBytes())) {invalidate("world decode budget");return;}
            try {update=encoded.decode();}
            catch(IllegalArgumentException error) {invalidate("unsupported chunk data");return;}
        }
        if(update instanceof WorldSnapshot.Chunk chunk && chunk.sectionCount()!=sections) {
            invalidate("chunk dimension mismatch");return;
        }
        if(update.estimatedBytes()>MAX_BYTES-bytes) {invalidate("decoded journal capacity");return;}
        int slot=(head+count)%MAX_UPDATES;
        updates[slot]=update;sequences[slot]=++sequence;chunkX[slot]=x;chunkZ[slot]=z;
        count++;bytes+=update.estimatedBytes();reason="world changes in flight";
        geometryRevision++;
    }

    void event(int kind,int id,long nano) {
        if(kind==NativePackets.CLOSED || kind==NativePackets.CONTEXT_CHANGE) {
            invalidate(kind==NativePackets.CLOSED?"observer unavailable":"context/barrier ambiguity");return;
        }
        if(kind==NativePackets.BARRIER_SENT) {
            for(int i=0;i<recentCount;i++) if(recentIds[i]==id) {invalidate("reused barrier ID");return;}
            recentIds[recentCursor++ & 15]=id;recentCount=Math.min(16,recentCount+1);
            if(barriers==MAX_BARRIERS) {invalidate("world barrier capacity");return;}
            ids[barriers]=id;sent[barriers]=nano;targets[barriers++]=sequence;return;
        }
        if(kind!=NativePackets.PONG)return;
        int found=-1;for(int i=0;i<barriers;i++)if(ids[i]==id){found=i;break;}
        if(found<0)return; // Foreign and already consumed replies cannot advance the world.
        if(found!=0 || nano<sent[0] || nano-sent[0]>PacketTimeline.TIMEOUT) {
            invalidate("out-of-order or expired world barrier");return;
        }
        long target=targets[0];
        if(count>0 && sequences[head]<=target)geometryRevision++;
        while(count>0 && sequences[head]<=target) {
            WorldSnapshot.Update update=updates[head];
            if(interested(chunkX[head],chunkZ[head])) confirmed.apply(update,sections,minSection,materializeBudget);
            bytes-=update.estimatedBytes();updates[head]=null;head=(head+1)%MAX_UPDATES;count--;commits++;
        }
        acknowledged=target;prefixKnown=true;
        System.arraycopy(ids,1,ids,0,--barriers);
        System.arraycopy(sent,1,sent,0,barriers);System.arraycopy(targets,1,targets,0,barriers);
        reason=count==0?"observed prefix acknowledged":"newer world changes pending";
    }

    void expire(long now) {
        if(barriers>0 && (now<sent[0] || now-sent[0]>PacketTimeline.TIMEOUT)) invalidate("world barrier timeout");
    }
    void invalidate(String why) {
        confirmed.clear();Arrays.fill(updates,null);head=count=bytes=barriers=0;
        prefixKnown=false;sequence++;resets++;geometryRevision++;reason=why;
    }
    private void setDimension(int min,int height,String key) {
        boolean valid=height>0 && height<=ChunkCodec.MAX_SECTIONS && min>=-2048 && min<=2048
                && key!=null && !key.isBlank() && key.length()<=256;
        minSection=min;sections=valid?height:0;dimension=valid?key:"unknown";
    }
    private boolean interested(int x,int z) {return Math.abs((long)x-centerX)<=1 && Math.abs((long)z-centerZ)<=1;}
    /** The only geometry lookup intended for a future predictor. Dirty chunks are unknown. */
    int stateId(int x,int y,int z) {
        if(!prefixKnown || sections==0 || !interested(x>>4,z>>4))return -1;
        for(int i=0;i<count;i++) {int slot=(head+i)%MAX_UPDATES;if(chunkX[slot]==x>>4 && chunkZ[slot]==z>>4)return -1;}
        return confirmed.stateId(x,y,z,minSection);
    }
    int pending() {return count;}
    int bytes() {return bytes;}
    long acknowledged() {return acknowledged;}
    String dimension() {return dimension;}
    long geometryRevision() {return geometryRevision;}
    String diagnostic() {
        return "World sync acknowledged="+prefixKnown+", sequence="+acknowledged+"/"+sequence+", pending="+count
                +", bytes="+bytes+", barriers="+barriers+", commits="+commits+", resets="+resets
                +", dimension="+dimension+", state="+reason+" (not proof of client obedience).";
    }
}
