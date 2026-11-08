package tsuzuki.host;

import java.util.concurrent.Semaphore;

final class Gate {
    private static final long SPACING_MS = 400;
    private final Semaphore slots = new Semaphore(2, true);
    private long nextStart;

    <T> T run(Work<T> work) throws Exception {
        slots.acquire();
        try {
            long wait;
            synchronized (this) {
                long now = System.currentTimeMillis();
                long start = Math.max(now, nextStart);
                nextStart = start + SPACING_MS;
                wait = start - now;
            }
            if (wait > 0) {
                Thread.sleep(wait);
            }
            return work.call();
        } finally {
            slots.release();
        }
    }

    interface Work<T> {
        T call() throws Exception;
    }
}
