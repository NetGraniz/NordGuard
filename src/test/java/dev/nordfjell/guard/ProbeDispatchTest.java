package dev.nordfjell.guard;

import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProbeDispatchTest {
    @Test void pausedLoopAccumulatesOneTaskOnly() {
        var clock=new AtomicLong(1);var queue=new ArrayDeque<Runnable>();var sent=new AtomicInteger();
        var dispatch=new ProbeDispatch(clock::get);
        for(int i=0;i<10000;i++)dispatch.submit(queue::add,sent::incrementAndGet);
        assertEquals(1,queue.size());assertEquals(0,sent.get());
        queue.remove().run();assertEquals(1,sent.get());
        assertTrue(dispatch.submit(queue::add,sent::incrementAndGet));
    }
    @Test void staleProbeDoesNotSendAndReleasesGate() {
        var clock=new AtomicLong(1);var queue=new ArrayDeque<Runnable>();var sent=new AtomicInteger();
        var dispatch=new ProbeDispatch(clock::get);dispatch.submit(queue::add,sent::incrementAndGet);
        clock.addAndGet(ProbeDispatch.MAX_AGE+1);queue.remove().run();assertEquals(0,sent.get());
        assertTrue(dispatch.submit(queue::add,sent::incrementAndGet));queue.remove().run();assertEquals(1,sent.get());
    }
    @Test void exactAgeBoundarySendsButFutureTimestampDoesNot() {
        var clock=new AtomicLong(100);var queue=new ArrayDeque<Runnable>();var sent=new AtomicInteger();
        var dispatch=new ProbeDispatch(clock::get);dispatch.submit(queue::add,sent::incrementAndGet);
        clock.addAndGet(ProbeDispatch.MAX_AGE);queue.remove().run();assertEquals(1,sent.get());
        dispatch.submit(queue::add,sent::incrementAndGet);clock.set(0);queue.remove().run();assertEquals(1,sent.get());
    }
    @Test void rejectionAndSendExceptionBothReleaseGate() {
        var dispatch=new ProbeDispatch(()->1);var queue=new ArrayDeque<Runnable>();
        assertThrows(RejectedExecutionException.class,()->dispatch.submit(r->{throw new RejectedExecutionException();},()->{}));
        assertTrue(dispatch.submit(queue::add,()->{throw new IllegalStateException();}));
        assertThrows(IllegalStateException.class,()->queue.remove().run());
        assertTrue(dispatch.submit(queue::add,()->{}));queue.remove().run();
    }
    @Test void nestedSchedulingCannotQueueBeforeSendFinishes() {
        var dispatch=new ProbeDispatch(()->1);var queue=new ArrayDeque<Runnable>();
        assertTrue(dispatch.submit(queue::add,()->assertFalse(dispatch.submit(queue::add,()->{}))));
        queue.remove().run();assertTrue(queue.isEmpty());assertTrue(dispatch.submit(queue::add,()->{}));
    }
    @Test void concurrentSubmitCannotQueueWhileSendIsExecuting() throws Exception {
        var dispatch=new ProbeDispatch(()->1);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()) {
            dispatch.submit(executor,()->{
                entered.countDown();
                try {if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("release timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
            });
            try {
                assertTrue(entered.await(3,TimeUnit.SECONDS));
                for(int i=0;i<1000;i++)assertFalse(dispatch.submit(executor,()->fail("duplicate send")));
            } finally {release.countDown();}
            executor.submit(()->{}).get(3,TimeUnit.SECONDS);
            assertTrue(dispatch.submit(executor,()->{}));
        }
    }
}
