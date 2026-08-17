package dev.minicodex.llm;

public final class ProviderException extends Exception {
    public enum Category {
        AUTHENTICATION,
        AUTHORIZATION,
        RATE_LIMIT,
        TRANSIENT,
        TIMEOUT,
        PROTOCOL,
        CONFIGURATION
    }

    private final Category category;
    private final boolean retryable;
    private final int statusCode;

    public ProviderException(Category category, boolean retryable, int statusCode, String message) {
        super(message);
        this.category = category;
        this.retryable = retryable;
        this.statusCode = statusCode;
    }

    public ProviderException(Category category, boolean retryable, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.retryable = retryable;
        this.statusCode = statusCode;
    }

    public Category category() {
        return category;
    }

    public boolean retryable() {
        return retryable;
    }

    public int statusCode() {
        return statusCode;
    }
}

