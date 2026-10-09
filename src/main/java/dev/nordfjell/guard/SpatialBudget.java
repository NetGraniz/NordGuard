package dev.nordfjell.guard;

import java.util.concurrent.atomic.AtomicLong;

/** Shared 50 ms windows bound world-cell reads even when many regions attack at once. */
final class SpatialBudget {
    private final AtomicLong state=new AtomicLong();
    boolean acquire(long now,int cells,int perSecond) {
        int window=(int)(now/50_000_000L),cap=perSecond/20;
        if(cells<1 || cells>cap) return false;
        while(true) {
            long old=state.get();int used=(int)(old>>>32)==window?(int)old:0;
            if(old!=0 && window-(int)(old>>>32)<0) return false;
            if(used>cap-cells) return false;
            long next=((long)window<<32)|Integer.toUnsignedLong(used+cells);
            if(state.compareAndSet(old,next)) return true;
        }
    }
}
