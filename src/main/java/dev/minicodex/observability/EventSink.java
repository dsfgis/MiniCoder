package dev.minicodex.observability;

@FunctionalInterface
public interface EventSink {
    void emit(RunEvent event);

    static EventSink noop() {
        return ignored -> { };
    }
}

