package dev.nordfjell.guard;

/** Entity-owner replica of outbound blocks only. Missing/evicted data are unknown, never air. */
final class ClientWorld {
    static final int MAX_CHUNKS=16, MAX_BYTES=512*1024;
    private final Cached[] chunks=new Cached[MAX_CHUNKS];
    private int bytes;
    private long clock, decoded, invalidated, evicted;
    private int lastX,lastZ;
    private Cached last;
    private static final class Cached {
        final WorldSnapshot.Chunk source;
        final int[][] edits;
        long use;
        int bytes;
        Cached(WorldSnapshot.Chunk source,long use) {
            this.source=source;this.use=use;this.edits=new int[source.sectionCount()][];
            bytes=source.estimatedBytes()+64+8*edits.length;
        }
    }
    void clear() { java.util.Arrays.fill(chunks,null);last=null;bytes=0;invalidated++; }
    void apply(WorldSnapshot.Update update,int expectedSections,int minSection) {
        apply(update,expectedSections,minSection,cells -> true);
    }
    void apply(WorldSnapshot.Update update,int expectedSections,int minSection,java.util.function.IntPredicate materializeBudget) {
        if(update instanceof WorldSnapshot.Invalidation || update instanceof WorldSnapshot.Reset) { clear();return; }
        if(update instanceof WorldSnapshot.Retain retain) {
            for(var chunk:chunks) if(chunk!=null && (Math.abs((long)chunk.source.x()-retain.x())>1 || Math.abs((long)chunk.source.z()-retain.z())>1))
                remove(chunk.source.x(),chunk.source.z());
            return;
        }
        if(update instanceof WorldSnapshot.Forget gone) { remove(gone.x(),gone.z());return; }
        if(update instanceof WorldSnapshot.EncodedChunk encoded) { apply(encoded.decode(),expectedSections,minSection,materializeBudget);return; }
        if(update instanceof WorldSnapshot.Chunk source) {
            if(source.sectionCount()!=expectedSections) { clear();return; }
            remove(source.x(),source.z());
            Cached next=new Cached(source,++clock);
            if(next.bytes>MAX_BYTES) { invalidated++;return; }
            makeRoom(next.bytes);
            int slot=0;while(slot<chunks.length && chunks[slot]!=null) slot++;
            if(slot==chunks.length) { evictOldest();slot=0;while(chunks[slot]!=null) slot++; }
            chunks[slot]=next;bytes+=next.bytes;last=null;decoded++;return;
        }
        if(update instanceof WorldSnapshot.Blocks blocks) {
            for(int i=0;i<blocks.count();i++) {
                Cached chunk=find(blocks.x(i)>>4,blocks.z(i)>>4);
                if(chunk==null) continue;
                int section=(blocks.y(i)>>4)-minSection;
                if(section<0 || section>=chunk.edits.length) { clear();return; }
                if(chunk.edits[section]==null) {
                    int extra=16+4096*4;
                    if(bytes+extra>MAX_BYTES || !materializeBudget.test(4096)) { remove(chunk.source.x(),chunk.source.z());invalidated++;continue; }
                    int[] edits=new int[4096];var original=chunk.source.section(section);
                    for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++)
                        edits[(y<<8)|(z<<4)|x]=original.stateId(x,y,z);
                    chunk.edits[section]=edits;chunk.bytes+=extra;bytes+=extra;
                }
                chunk.edits[section][((blocks.y(i)&15)<<8)|((blocks.z(i)&15)<<4)|(blocks.x(i)&15)]=blocks.stateId(i);
            }
        }
    }
    int stateId(int x,int y,int z,int minSection) {
        Cached chunk=find(x>>4,z>>4);if(chunk==null)return -1;
        int section=(y>>4)-minSection;
        if(section<0||section>=chunk.edits.length)return -1;
        int[] edits=chunk.edits[section];
        return edits==null?chunk.source.section(section).stateId(x&15,y&15,z&15):edits[((y&15)<<8)|((z&15)<<4)|(x&15)];
    }
    private Cached find(int x,int z) {
        if(last!=null && lastX==x && lastZ==z) {last.use=++clock;return last;}
        for(var cached:chunks) if(cached!=null && cached.source.x()==x && cached.source.z()==z) {
            lastX=x;lastZ=z;last=cached;cached.use=++clock;return cached;
        }
        last=null;return null;
    }
    private void remove(int x,int z) {
        for(int i=0;i<chunks.length;i++) if(chunks[i]!=null && chunks[i].source.x()==x && chunks[i].source.z()==z) {
            bytes-=chunks[i].bytes;chunks[i]=null;last=null;return;
        }
    }
    private void makeRoom(int requested) { while(bytes+requested>MAX_BYTES) evictOldest(); }
    private void evictOldest() {
        int oldest=-1;
        for(int i=0;i<chunks.length;i++) if(chunks[i]!=null && (oldest<0||chunks[i].use<chunks[oldest].use)) oldest=i;
        if(oldest<0) throw new IllegalStateException("Replica accounting mismatch");
        bytes-=chunks[oldest].bytes;chunks[oldest]=null;last=null;evicted++;
    }
    int bytes() { return bytes; }
    int size() { int count=0;for(var c:chunks)if(c!=null)count++;return count; }
    long decoded() {return decoded;}
    long invalidated() {return invalidated;}
    long evicted() {return evicted;}
}
