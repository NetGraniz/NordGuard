package dev.nordfjell.guard;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ChunkCodecTest {
    @Test void singletonIncludesTwoCountsAndNoLongArrayLength() throws IOException {
        byte[] bytes=section(0,new int[]{12345},new long[0],0);
        WorldSnapshot.Section[] decoded=ChunkCodec.decode(bytes);
        assertEquals(1,decoded.length);
        assertEquals(12345,decoded[0].stateId(0,0,0));
        assertEquals(12345,decoded[0].stateId(15,15,15));
        assertEquals(-1,decoded[0].stateId(16,0,0));
        assertTrue(decoded[0].estimatedBytes()<128);
    }

    @Test void fiveBitStoragePadsEachLongAndUsesYzxIndex() throws IOException {
        int bits=5,perLong=64/bits;
        long[] packed=new long[(4096+perLong-1)/perLong];
        int[] palette=new int[32];
        for(int i=0;i<palette.length;i++) palette[i]=100+i;
        for(int i=0;i<4096;i++) packed[i/perLong]|=(long)(i%32)<<((i%perLong)*bits);
        WorldSnapshot.Section decoded=ChunkCodec.decode(section(bits,palette,packed,3))[0];
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++)
            assertEquals(100+(((y<<8)|(z<<4)|x)%32),decoded.stateId(x,y,z));
    }

    @Test void globalPaletteUsesDirectStateIdsAndBiomesHaveNoLocalPalette() throws IOException {
        int bits=15,perLong=64/bits;
        long[] packed=new long[(4096+perLong-1)/perLong];
        packed[0]=17000L|(30000L<<15);
        WorldSnapshot.Section decoded=ChunkCodec.decode(section(bits,null,packed,7))[0];
        assertEquals(17000,decoded.stateId(0,0,0));
        assertEquals(30000,decoded.stateId(1,0,0));
        assertTrue(decoded.estimatedBytes()<9000);
    }

    @Test void sectionsFollowOneAnotherWithoutLengthPrefixes() throws IOException {
        byte[] first=section(0,new int[]{17},new long[0],1);
        byte[] second=section(0,new int[]{29},new long[0],0);
        byte[] combined=new byte[first.length+second.length];
        System.arraycopy(first,0,combined,0,first.length);
        System.arraycopy(second,0,combined,first.length,second.length);
        WorldSnapshot.Section[] decoded=ChunkCodec.decode(combined);
        assertEquals(2,decoded.length);
        assertEquals(17,decoded[0].stateId(0,0,0));
        assertEquals(29,decoded[1].stateId(0,0,0));
    }

    @Test void truncatedInvalidAndExcessiveInputsCannotBecomeAir() throws IOException {
        byte[] bytes=section(4,new int[]{23},new long[256],2);
        for(int length=0;length<bytes.length;length++) {
            byte[] truncated=Arrays.copyOf(bytes,length);
            assertThrows(IllegalArgumentException.class,()->ChunkCodec.decode(truncated));
        }
        byte[] invalidBits=bytes.clone();invalidBits[4]=3;
        assertThrows(IllegalArgumentException.class,()->ChunkCodec.decode(invalidBits));
        byte[] negativeId=section(0,new int[]{-1},new long[0],0);
        assertThrows(IllegalArgumentException.class,()->ChunkCodec.decode(negativeId));
        assertThrows(IllegalArgumentException.class,()->ChunkCodec.decode(new byte[ChunkCodec.MAX_BYTES+1]));
        byte[] singleton=section(0,new int[]{0},new long[0],0);
        byte[] tooMany=new byte[singleton.length*(ChunkCodec.MAX_SECTIONS+1)];
        for(int i=0;i<=ChunkCodec.MAX_SECTIONS;i++) System.arraycopy(singleton,0,tooMany,i*singleton.length,singleton.length);
        assertThrows(IllegalArgumentException.class,()->ChunkCodec.decode(tooMany));
    }

    @Test void unknownPaletteIndexIsUnknownAndPayloadConstructorCopies() throws IOException {
        long[] packed=new long[256];packed[0]=3;
        byte[] bytes=section(4,new int[]{5},packed,0);
        WorldSnapshot.EncodedChunk encoded=new WorldSnapshot.EncodedChunk(2,-3,bytes);
        Arrays.fill(bytes,(byte)0);
        Arrays.fill(encoded.payload(),(byte)0);
        WorldSnapshot.Chunk decoded=encoded.decode();
        assertEquals(2,decoded.x());assertEquals(-3,decoded.z());
        assertEquals(-1,decoded.section(0).stateId(0,0,0));
        assertEquals(5,decoded.section(0).stateId(1,0,0));
        assertTrue(decoded.estimatedBytes()<encoded.estimatedBytes()+256);
    }

    private static byte[] section(int bits,int[] palette,long[] packed,int biomeBits) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(4096);out.writeShort(0);
        out.writeByte(bits);
        if(bits==0) varInt(out,palette[0]);
        else if(bits<=8) { varInt(out,palette.length);for(int id:palette) varInt(out,id); }
        for(long value:packed) out.writeLong(value);
        out.writeByte(biomeBits);
        if(biomeBits==0) varInt(out,0);
        else {
            if(biomeBits<=3) { varInt(out,1);varInt(out,0); }
            int perLong=64/biomeBits;
            for(int i=0;i<(64+perLong-1)/perLong;i++) out.writeLong(0);
        }
        return bytes.toByteArray();
    }
    private static void varInt(DataOutputStream out,int value) throws IOException {
        do {
            int next=value&127;value>>>=7;
            out.writeByte(value==0?next:next|128);
        } while(value!=0);
    }
}
