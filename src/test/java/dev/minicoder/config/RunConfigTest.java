package dev.minicoder.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证运行配置与 Provider 专用环境变量的优先级、必填项和跨供应商隔离。
 *
 * @author Self David (dsfgis@gmail.com)
 */
class RunConfigTest {
    private static final String DEEPSEEK_TEST_KEY = "sk-deepseek-test-secret-0000";
    private static final String OPENAI_TEST_KEY = "sk-openai-test-secret-0000";

    @Test
    void rejectsInvalidBudgets() {
        assertThrows(IllegalArgumentException.class, () -> new RunConfig(
                Path.of("."), "task", "fake", "model", 0, Duration.ofSeconds(1),
                Optional.empty(), false, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new RunConfig(
                Path.of("."), "task", "fake", "model", 1, Duration.ZERO,
                Optional.empty(), false, Optional.empty()));
    }

    @Test
    void resolvesDeepSeekConfigurationWithCliPrecedenceAndRedactedRepresentation() {
        ProviderConfig config = ProviderConfig.resolve("DEEPSEEK", "cli-model", "https://gateway.example/v1",
                Map.of("DEEPSEEK_API_KEY", DEEPSEEK_TEST_KEY,
                        "DEEPSEEK_MODEL", "environment-model",
                        "DEEPSEEK_BASE_URL", "https://environment.example"));

        assertEquals(ProviderConfig.DEEPSEEK, config.provider());
        assertEquals("cli-model", config.model());
        assertEquals("https://gateway.example/v1", config.baseUrl().toString());
        assertEquals(DEEPSEEK_TEST_KEY, config.apiKey());
        assertFalse(config.toString().contains(DEEPSEEK_TEST_KEY));
        assertTrue(config.toString().contains("[REDACTED]"));
    }

    @Test
    void keepsProviderSecretsIsolatedAndRequiresDeepSeekModel() {
        ConfigException missingKey = assertThrows(ConfigException.class, () -> ProviderConfig.resolve(
                "deepseek", "deepseek-test", null,
                Map.of("OPENAI_API_KEY", OPENAI_TEST_KEY)));
        assertEquals("Missing DEEPSEEK_API_KEY", missingKey.getMessage());
        assertFalse(missingKey.getMessage().contains(OPENAI_TEST_KEY));

        ConfigException missingModel = assertThrows(ConfigException.class, () -> ProviderConfig.resolve(
                "deepseek", null, null, Map.of("DEEPSEEK_API_KEY", DEEPSEEK_TEST_KEY)));
        assertTrue(missingModel.getMessage().contains("DEEPSEEK_MODEL"));
        assertFalse(missingModel.getMessage().contains(DEEPSEEK_TEST_KEY));
    }

    @Test
    void usesProviderSpecificDefaultsWithoutReadingOtherProviderValues() {
        ProviderConfig deepSeek = ProviderConfig.resolve("deepseek", null, null,
                Map.of("DEEPSEEK_API_KEY", DEEPSEEK_TEST_KEY, "DEEPSEEK_MODEL", "deepseek-model",
                        "OPENAI_MODEL", "openai-model", "OPENAI_BASE_URL", "https://openai.example"));
        assertEquals("deepseek-model", deepSeek.model());
        assertEquals("https://api.deepseek.com", deepSeek.baseUrl().toString());

        ProviderConfig scripted = ProviderConfig.resolve("scripted", null, null, Map.of());
        assertEquals("scripted-v0.1", scripted.model());
        assertThrows(IllegalStateException.class, scripted::apiKey);
    }
}
