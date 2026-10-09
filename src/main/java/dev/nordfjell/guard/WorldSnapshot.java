package dev.nordfjell.guard;

import java.util.Objects;

/** Detached outbound data. Arrays never escape to callers or retain a native packet. */
final class WorldSnapshot {
    private WorldSnapshot() {}

    sealed interface Update permits EncodedChunk, Chunk, Blocks, Forget, Reset, Invalidation, Retain {
        int estimatedBytes();
    }

    static final class EncodedChunk implements Update {
        private final int x,z;
        private final byte[] payload;
        EncodedChunk(int x,int z,byte[] payload) { this(x,z,payload.clone(),true); }
        private EncodedChunk(int x,int z,byte[] payload,boolean owned) {
            this.x=x;this.z=z;this.payload=Objects.requireNonNull(payload);
        }
        static EncodedChunk owned(int x,int z,byte[] payload) { return new EncodedChunk(x,z,payload,true); }
        int x() { return x; }
        int z() { return z; }
        byte[] payload() { return payload.clone(); }
        Chunk decode() { return new Chunk(x,z,ChunkCodec.decode(payload)); }
        public int estimatedBytes() { return 48+payload.length; }
    }

    static final class Chunk implements Update {
        private final int x,z,estimatedBytes;
        private final Section[] sections;
        Chunk(int x,int z,Section[] sections) {
            this.x=x;this.z=z;this.sections=sections.clone();
            int bytes=48+8*sections.length;
            for(Section section:this.sections) bytes+=Objects.requireNonNull(section).estimatedBytes();
            estimatedBytes=bytes;
        }
        int x() { return x; }
        int z() { return z; }
        int sectionCount() { return sections.length; }
        Section section(int index) { return sections[index]; }
        public int estimatedBytes() { return estimatedBytes; }
    }

    static final class Section {
        private final int bits,valuesPerLong;
        private final long mask;
        private final int[] palette;
        private final long[] packed;
        // Codec transfers its new arrays; it must not retain or modify them afterwards.
        Section(int bits,int[] palette,long[] packed) {
            this.bits=bits;this.palette=palette;this.packed=packed;
            valuesPerLong=bits==0?0:64/bits;mask=bits==0?0:(1L<<bits)-1;
        }
        int bits() { return bits; }
        int stateId(int x,int y,int z) {
            if((x|y|z)<0||x>15||y>15||z>15) return -1;
            if(bits==0) return palette[0];
            int index=(y<<8)|(z<<4)|x;
            int value=(int)((packed[index/valuesPerLong]>>>((index%valuesPerLong)*bits))&mask);
            return palette==null?value:(value<palette.length?palette[value]:-1);
        }
        int estimatedBytes() { return 64+(palette==null?0:16+4*palette.length)+16+8*packed.length; }
    }

    static final class Blocks implements Update {
        private final int[] entries;
        Blocks(int[] entries) { this(entries.clone(),true); }
        private Blocks(int[] entries,boolean owned) {
            if(entries.length%4!=0||entries.length>4096*4) throw new IllegalArgumentException("Invalid block batch");
            this.entries=entries;
        }
        static Blocks owned(int[] entries) { return new Blocks(entries,true); }
        int count() { return entries.length/4; }
        int x(int index) { return entries[index*4]; }
        int y(int index) { return entries[index*4+1]; }
        int z(int index) { return entries[index*4+2]; }
        int stateId(int index) { return entries[index*4+3]; }
        public int estimatedBytes() { return 32+4*entries.length; }
    }
    record Forget(int x,int z) implements Update { public int estimatedBytes() { return 24; } }
    record Reset() implements Update { public int estimatedBytes() { return 16; } }
    record Invalidation() implements Update { public int estimatedBytes() { return 16; } }
    record Retain(int x,int z) implements Update { public int estimatedBytes() {return 24;} }
}
