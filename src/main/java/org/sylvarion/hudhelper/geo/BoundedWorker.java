package org.sylvarion.hudhelper.geo;

import java.util.concurrent.*;
import java.util.function.Function;

/** One outstanding batch total, with backpressure instead of an unbounded executor queue. */
public final class BoundedWorker<I,O> implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Function<I,O> calculation;
    private Future<O> pending;
    private boolean closed;

    public BoundedWorker(String name,Function<I,O> calculation) {
        this.calculation=calculation;
        executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),runnable -> {
            Thread thread=new Thread(runnable,name);
            thread.setDaemon(true);
            return thread;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    public synchronized boolean submit(I input) {
        if (closed || pending != null) return false;
        pending=executor.submit(() -> calculation.apply(input));
        return true;
    }
    public synchronized boolean busy() { return pending != null; }
    /** Never waits on the server thread. A completed failed batch is released for retry. */
    public synchronized O poll() {
        if (pending == null || !pending.isDone()) return null;
        Future<O> completed=pending;
        pending=null;
        try { return completed.get(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted reading location batch",ex); }
        catch (ExecutionException ex) { throw new IllegalStateException("Location worker failed",ex.getCause()); }
    }
    @Override public synchronized void close() {
        closed=true;
        if (pending != null) pending.cancel(true);
        pending=null;
        executor.shutdownNow();
    }
}
