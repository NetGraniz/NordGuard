package dev.nordfjell.guard;

/** Consecutive owner observations, not claimed client ground or packet counts. */
final class SurfaceEvidence {
    private int samples;
    private double originY;
    private long last;
    void reset(){samples=0;last=0;}
    boolean accept(boolean eligible,double y,long now,int threshold,long maxGap) {
        if(!eligible||!Double.isFinite(y)){reset();return false;}
        if(samples==0||now<=last||now-last>maxGap||Math.abs(y-originY)>.1){samples=0;originY=y;}
        last=now;samples=Math.min(threshold,samples+1);
        return samples>=threshold;
    }
}
