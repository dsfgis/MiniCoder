package dev.minicoder.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证精确秘密、Bearer header、API key 字段和常见令牌形态均被统一遮蔽。
 *
 * @author Self David (dsfgis@gmail.com)
 */
class RedactorTest {
    @Test
    void redactsConfiguredAndPatternSecrets() {
        Redactor redactor = new Redactor(List.of("unique-secret-value"));
        String output = redactor.redact("Authorization: Bearer abcdef api_key=xyz unique-secret-value sk-abcdefghijklmnop");
        assertFalse(output.contains("unique-secret-value"));
        assertFalse(output.contains("abcdef"));
        assertFalse(output.contains("sk-abcdefghijklmnop"));
        assertTrue(output.contains("[REDACTED]"));
    }
}
