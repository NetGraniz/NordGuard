package dev.nordfjell.guard;

import java.util.Arrays;

/** Exact 26.2 outbound section format, not the pre-26.x length-prefixed long-array format. */
final class ChunkCodec {
    static final int MAX_BYTES=256*1024, MAX_SECTIONS=64;
    private ChunkCodec() {}

    static WorldSnapshot.Section[] decode(byte[] payload) {
        if(payload==null||payload.length==0||payload.length>MAX_BYTES)
            throw new IllegalArgumentException("Unsupported chunk payload size");
        Cursor cursor=new Cursor(payload);
        WorldSnapshot.Section[] sections=new WorldSnapshot.Section[MAX_SECTIONS];
        int count=0;
        while(cursor.remaining()>0) {
            if(count==MAX_SECTIONS) throw new IllegalArgumentException("Too many chunk sections");
            if(cursor.unsignedShort()>4096||cursor.unsignedShort()>4096)
                throw new IllegalArgumentException("Invalid section block/fluid count");
            sections[count++]=readBlocks(cursor);
            skipBiomes(cursor);
        }
        return Arrays.copyOf(sections,count);
    }
    private static WorldSnapshot.Section readBlocks(Cursor cursor) {
        int bits=cursor.unsignedByte();
        if(bits==0) return new WorldSnapshot.Section(0,new int[]{cursor.varInt()},new long[0]);
        // A native server writes canonical widths, unlike a permissive incoming decoder.
        if(bits<4||bits>31) throw new IllegalArgumentException("Invalid block-state bit width");
        int[] palette=null;
        if(bits<=8) {
            int count=cursor.varInt();
            if(count<1||count>(1<<bits)) throw new IllegalArgumentException("Invalid block palette size");
            palette=new int[count];
            for(int i=0;i<count;i++) palette[i]=cursor.varInt();
        }
        int perLong=64/bits,words=(4096+perLong-1)/perLong;
        cursor.require(words*8);
        long[] packed=new long[words];
        for(int i=0;i<words;i++) packed[i]=cursor.longValue();
        return new WorldSnapshot.Section(bits,palette,packed);
    }
    private static void skipBiomes(Cursor cursor) {
        int bits=cursor.unsignedByte();
        if(bits>31) throw new IllegalArgumentException("Invalid biome bit width");
        if(bits==0) { cursor.varInt();return; }
        if(bits<=3) {
            int count=cursor.varInt();
            if(count<1||count>(1<<bits)) throw new IllegalArgumentException("Invalid biome palette size");
            for(int i=0;i<count;i++) cursor.varInt();
        }
        int perLong=64/bits,words=(64+perLong-1)/perLong;
        cursor.skip(words*8);
    }
    private static final class Cursor {
        private final byte[] bytes;
        private int index;
        Cursor(byte[] bytes) { this.bytes=bytes; }
        int remaining() { return bytes.length-index; }
        void require(int length) {
            if(length<0||length>remaining()) throw new IllegalArgumentException("Truncated chunk section");
        }
        void skip(int length) { require(length);index+=length; }
        int unsignedByte() { require(1);return bytes[index++]&255; }
        int unsignedShort() { return (unsignedByte()<<8)|unsignedByte(); }
        int varInt() {
            int value=0;
            for(int shift=0;shift<35;shift+=7) {
                int next=unsignedByte();
                if(shift==28&&(next&0xf8)!=0) throw new IllegalArgumentException("Invalid positive registry VarInt");
                value|=(next&127)<<shift;
                if((next&128)==0) return value;
            }
            throw new IllegalArgumentException("Oversize registry VarInt");
        }
        long longValue() {
            require(8);
            long value=0;
            for(int i=0;i<8;i++) value=(value<<8)|(bytes[index++]&255L);
            return value;
        }
    }
}
