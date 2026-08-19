package dev.minicoder.agent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在线程之间传递协作式取消信号，避免运行时继续发起新的 Provider 或工具调用。
 *
 * @author Self David (dsfgis@gmail.com)
 */
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
