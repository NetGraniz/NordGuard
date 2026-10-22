package dev.nordfjell.guard;

/** Event-loop producer, entity-owner consumer. No live entity references or packet-count allowance. */
final class AttackEvidence {
    private static final int MOVES=8,CAPACITY=16;
    private final long[] moveTime=new long[MOVES];
    private final double[] moveX=new double[MOVES],moveY=new double[MOVES],moveZ=new double[MOVES];
    private final boolean[] moveGround=new boolean[MOVES];
    private int moveCursor,moveCount;
    private final int[] targets=new int[CAPACITY],counts=new int[CAPACITY];
    private final long[] times=new long[CAPACITY],epochs=new long[CAPACITY],oldest=new long[CAPACITY];
    private final double[] base=new double[CAPACITY],peak=new double[CAPACITY],groundPeak=new double[CAPACITY],
            spanX=new double[CAPACITY],spanZ=new double[CAPACITY];
    private volatile long written,read,dropped;
    void move(long now,double x,double y,double z,boolean ground) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)){moveCount=0;return;}
        int i=moveCursor++&(MOVES-1);moveTime[i]=now;moveX[i]=x;moveY[i]=y;moveZ[i]=z;moveGround[i]=ground;
        moveCount=Math.min(MOVES,moveCount+1);
    }
    void resetMoves() {moveCount=0;}
    void attack(int target,long now,long epoch) {
        attack(target,now,epoch,250_000_000L);
    }
    void attack(int target,long now,long epoch,long historyNanos) {
        long w=written;if(w-read>=CAPACITY){dropped++;moveCount=0;return;}
        int slot=(int)w&(CAPACITY-1),latest=(moveCursor-1)&(MOVES-1),count=0;
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,
                minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,gp=Double.NEGATIVE_INFINITY;
        long first=now;
        for(int n=0;n<moveCount;n++) {
            int i=(moveCursor-1-n)&(MOVES-1);
            if(now<moveTime[i]||now-moveTime[i]>historyNanos)break;
            count++;first=moveTime[i];minX=Math.min(minX,moveX[i]);maxX=Math.max(maxX,moveX[i]);
            minZ=Math.min(minZ,moveZ[i]);maxZ=Math.max(maxZ,moveZ[i]);maxY=Math.max(maxY,moveY[i]);
            if(moveGround[i])gp=Math.max(gp,moveY[i]);
        }
        targets[slot]=target;times[slot]=now;epochs[slot]=epoch;oldest[slot]=first;counts[slot]=count;
        base[slot]=moveCount==0?Double.NaN:moveY[latest];peak[slot]=maxY;groundPeak[slot]=gp;
        spanX[slot]=maxX-minX;spanZ[slot]=maxZ-minZ;written=w+1;
    }
    private long seenDropped;
    boolean poll(int target,long now,long epoch,CombatLimits limits,Cursor out) {
        out.valid=false;long w=written;
        if(dropped!=seenDropped){seenDropped=dropped;read=w;return false;}
        long r=read;if(r==w)return false;int i=(int)r&(CAPACITY-1);
        // A missing/cancelled native attack can shift the stream. Never borrow a future matching token.
        if(targets[i]!=target||epochs[i]!=epoch||now<times[i]||now-times[i]>limits.attackEvidenceMillis()*1_000_000L) {
            read=w;return false;
        }
        out.baseY=base[i];out.peakY=peak[i];out.groundPeakY=groundPeak[i];out.time=times[i];out.epoch=epochs[i];
        out.valid=counts[i]>=4 && times[i]>=oldest[i] && times[i]-oldest[i]<=limits.attackEvidenceMillis()*1_000_000L
                && Double.isFinite(spanX[i]) && Double.isFinite(spanZ[i])
                && spanX[i]<=limits.criticalHorizontalSpan() && spanZ[i]<=limits.criticalHorizontalSpan();
        read=r+1;return true;
    }
    static final class Cursor {
        boolean valid;double baseY,peakY,groundPeakY;long time,epoch;
        boolean anomalous(CombatLimits c) {
            double rise=peakY-baseY,groundRise=groundPeakY-baseY;
            return valid&&Double.isFinite(baseY)&&Double.isFinite(rise)&&Double.isFinite(groundRise)
                    &&groundRise>=c.criticalMinRise()&&rise<=c.criticalMaxRise()&&rise>=groundRise;
        }
    }
}
