package dev.minicoder.observability;

/**
 * 接收一次运行产生的结构化事件，供内存收集、日志或后续观察实现使用。
 *
 * @author Self David (dsfgis@gmail.com)
 */
@FunctionalInterface
public interface EventSink {
    void emit(RunEvent event);

    static EventSink noop() {
        return ignored -> { };
    }
}
