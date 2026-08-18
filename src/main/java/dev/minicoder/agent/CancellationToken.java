package dev.minicoder.agent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public boolean isCancelled() {
        return cancelled.get() || Thread.currentThread().isInterrupted();
    }

    public void cancel() {
        cancelled.set(true);
    }

    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Run was cancelled");
        }
    }

    public static final class CancellationException extends RuntimeException {
        public CancellationException(String message) {
            super(message);
        }
    }
}

