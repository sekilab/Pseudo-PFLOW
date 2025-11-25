package pseudo.gen;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public final class FirstCallGate {
    private static final ConcurrentHashMap<Object, CountDownLatch> gates = new ConcurrentHashMap<>();

    public static void ensureOnce(Object key, Runnable warmup) {
        CountDownLatch latch = gates.computeIfAbsent(key, k -> new CountDownLatch(1));
        if (latch.getCount() == 0) return;          // 已预热
        synchronized (latch) {                      // 只放一个线程做首调
            if (latch.getCount() == 0) return;
            try { warmup.run(); } finally { latch.countDown(); }
        }
    }

    public static void await(Object key) {
        CountDownLatch latch = gates.get(key);
        if (latch != null) try { latch.await(); } catch (InterruptedException ignored) {}
    }
}

