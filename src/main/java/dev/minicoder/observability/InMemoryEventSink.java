package dev.minicoder.observability;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 在线程安全的内存列表中收集运行事件，便于测试按顺序审计 Agent 行为。
 *
 * @author Self David (dsfgis@gmail.com)
 */
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
