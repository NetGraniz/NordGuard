package dev.nordfjell.guard;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** At most one queued/executing probe, even while the channel event loop stalls. */
final class ProbeDispatch {
    static final long MAX_AGE=500_000_000L;
    private final AtomicBoolean queued=new AtomicBoolean();
    private final LongSupplier clock;
    ProbeDispatch() {this(System::nanoTime);}
    ProbeDispatch(LongSupplier clock) {this.clock=clock;}
    boolean submit(Executor executor,Runnable send) {
        if(!queued.compareAndSet(false,true)) return false;
        long created=clock.getAsLong();
        try {
            executor.execute(()->{
                try {
                    long now=clock.getAsLong();
                    if(now>=created && now-created<=MAX_AGE) send.run();
                } finally {queued.set(false);}
            });
        } catch(RuntimeException failure) {queued.set(false);throw failure;}
        return true;
    }
}
