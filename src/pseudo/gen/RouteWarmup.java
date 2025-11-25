package pseudo.gen;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RouteWarmup {
    private static final AtomicBoolean done = new AtomicBoolean(false);
    private static final CountDownLatch latch = new CountDownLatch(1);
    private RouteWarmup() {}

    /** 只允许一个线程执行 warmup，其余线程等待。 */
    public static void ensureOnce(Runnable warmup) {
        if (done.get()) return;
        synchronized (RouteWarmup.class) {
            if (done.get()) return;
            try { warmup.run(); } finally {
                done.set(true);
                latch.countDown();
            }
        }
    }
    public static void await() {
        if (!done.get()) try { latch.await(); } catch (InterruptedException ignored) {}
    }
}

