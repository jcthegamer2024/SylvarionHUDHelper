package org.sylvarion.hudhelper.geo;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import static org.junit.jupiter.api.Assertions.*;

class BoundedWorkerTest {
    private <T> T await(BoundedWorker<?,T> worker) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime()<deadline) {
            T result=worker.poll();
            if(result!=null) return result;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        throw new AssertionError("Worker did not complete");
    }

    @Test void executesOnDedicatedThreadAndRejectsBacklogWithoutBlocking() throws Exception {
        Thread caller=Thread.currentThread();
        AtomicReference<Thread> calculationThread=new AtomicReference<>();
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        try (var worker=new BoundedWorker<Integer,Integer>("location-test",value -> {
            calculationThread.set(Thread.currentThread()); started.countDown();
            try { if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("Release timeout"); }
            catch (InterruptedException ex) { throw new IllegalStateException(ex); }
            return value*2;
        })) {
            assertTrue(worker.submit(5));
            assertTrue(started.await(5,TimeUnit.SECONDS));
            assertNotEquals(caller,calculationThread.get());
            assertTrue(calculationThread.get().isDaemon());
            for(int i=0;i<10000;i++) assertFalse(worker.submit(i));
            assertNull(worker.poll());
            release.countDown();
            assertEquals(10,await(worker)); assertFalse(worker.busy());
            assertTrue(worker.submit(8)); assertEquals(16,await(worker));
        } finally { release.countDown(); }
    }

    @Test void completedButUnpolledWorkCannotAccumulateQueuedJobs() throws Exception {
        CountDownLatch calculated=new CountDownLatch(1);
        try(var worker=new BoundedWorker<Integer,Integer>("location-test",value -> { calculated.countDown(); return value; })) {
            assertTrue(worker.submit(1)); assertTrue(calculated.await(5,TimeUnit.SECONDS));
            assertFalse(worker.submit(2));
            assertEquals(1,await(worker));
            assertTrue(worker.submit(3)); assertEquals(3,await(worker));
        }
    }

    @Test void calculationFailureIsReleasedAndLaterWorkCanRun() {
        try(var worker=new BoundedWorker<Integer,Integer>("location-test",value -> {
            if(value==1) throw new IllegalArgumentException("test failure");
            return value;
        })) {
            worker.submit(1);
            IllegalStateException failure=assertThrows(IllegalStateException.class,() -> await(worker));
            assertInstanceOf(IllegalArgumentException.class,failure.getCause());
            assertFalse(worker.busy());
            assertTrue(worker.submit(2)); assertEquals(2,await(worker));
        }
    }

    @Test void closeInterruptsRunningWorkAndRejectsFurtherSubmissions() throws Exception {
        CountDownLatch started=new CountDownLatch(1),interrupted=new CountDownLatch(1);
        var worker=new BoundedWorker<Integer,Integer>("location-test",value -> {
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch(InterruptedException ex) { interrupted.countDown(); }
            return value;
        });
        try {
            worker.submit(1); assertTrue(started.await(5,TimeUnit.SECONDS));
            worker.close();
            assertFalse(worker.submit(2)); assertNull(worker.poll());
            assertTrue(interrupted.await(5,TimeUnit.SECONDS));
        } finally { worker.close(); }
    }
}
