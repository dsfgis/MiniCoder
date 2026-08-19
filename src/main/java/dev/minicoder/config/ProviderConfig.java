package dev.minicoder.config;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 按 Provider 隔离解析 API key、模型与 Base URL，禁止跨供应商回退凭据。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class ProviderConfig {
    public static final String OPENAI = "openai";
    public static final String DEEPSEEK = "deepseek";
    public static final String SCRIPTED = "scripted";

    private final String provider;
    private final String model;
    private final URI baseUrl;
    private final String apiKey;

    private ProviderConfig(String provider, String model, URI baseUrl, String apiKey) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public static ProviderConfig resolve(String providerOption, String modelOption, String baseUrlOption,
                                         Map<String, String> environment) {
        Map<String, String> env = Map.copyOf(Objects.requireNonNullElse(environment, Map.of()));
        return resolve(providerOption, modelOption, baseUrlOption, env::get);
    }

    public static ProviderConfig resolve(String providerOption, String modelOption, String baseUrlOption,
                                         Function<String, String> environment) {
        Function<String, String> env = Objects.requireNonNull(environment, "environment");
        String provider = firstNonBlank(providerOption, OPENAI).toLowerCase(Locale.ROOT);
        return switch (provider) {
            case SCRIPTED -> new ProviderConfig(SCRIPTED,
                    firstNonBlank(modelOption, "scripted-v0.1"), null, null);
            case OPENAI -> remote(OPENAI, modelOption, baseUrlOption, env,
                    "OPENAI_MODEL", "OPENAI_BASE_URL", "OPENAI_API_KEY", "https://api.openai.com");
            case DEEPSEEK -> remote(DEEPSEEK, modelOption, baseUrlOption, env,
                    "DEEPSEEK_MODEL", "DEEPSEEK_BASE_URL", "DEEPSEEK_API_KEY", "https://api.deepseek.com");
            default -> throw new ConfigException(
                    "V0.1 supports openai, deepseek, and the offline scripted Provider");
        };
    }

    private static ProviderConfig remote(String provider, String modelOption, String baseUrlOption,
                                         Function<String, String> env, String modelVariable, String baseUrlVariable,
                                         String keyVariable, String defaultBaseUrl) {
        String model = firstNonBlank(modelOption, env.apply(modelVariable));
        if (model == null) {
            throw new ConfigException("Missing model: use --model or " + modelVariable);
        }
        String apiKey = firstNonBlank(env.apply(keyVariable));
        if (apiKey == null) {
            throw new ConfigException("Missing " + keyVariable);
        }
        String baseUrl = firstNonBlank(baseUrlOption, env.apply(baseUrlVariable), defaultBaseUrl);
        try {
            return new ProviderConfig(provider, model, URI.create(baseUrl), apiKey);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Invalid " + provider + " Base URL", e);
        }
    }

    public String provider() {
        return provider;
    }

    public String model() {
        return model;
    }

    public URI baseUrl() {
        if (baseUrl == null) throw new IllegalStateException("scripted Provider has no Base URL");
        return baseUrl;
    }

    public String apiKey() {
        if (apiKey == null) throw new IllegalStateException("scripted Provider has no API key");
        return apiKey;
    }

    @Override
    public String toString() {
        return "ProviderConfig[provider=" + provider + ", model=" + model + ", baseUrl=" + baseUrl
                + ", apiKey=" + (apiKey == null ? "none" : "[REDACTED]") + "]";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.strip();
        }
        return null;
    }
}
