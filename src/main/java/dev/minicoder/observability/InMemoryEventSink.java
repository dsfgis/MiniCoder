package dev.minicoder.observability;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryEventSink implements EventSink {
    private final CopyOnWriteArrayList<RunEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void emit(RunEvent event) {
        events.add(event);
    }

    public List<RunEvent> events() {
        return List.copyOf(events);
    }
}
