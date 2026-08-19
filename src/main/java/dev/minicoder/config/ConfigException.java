package dev.minicoder.config;

/**
 * 表示 CLI 配置缺失、冲突或格式不合法等应在执行副作用前报告的错误。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
