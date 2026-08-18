package dev.minicoder.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class Redactor {
    private static final String MASK = "[REDACTED]";
    private static final List<Pattern> TOKEN_PATTERNS = List.of(
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[A-Za-z0-9._~+/=-]+"),
            Pattern.compile("(?i)(api[-_]?key\\s*[:=]\\s*)[A-Za-z0-9._~+/=-]+"),
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{12,}\\b")
    );

    private final List<String> secrets;

    public Redactor(Collection<String> secrets) {
        this.secrets = Objects.requireNonNullElse(secrets, List.<String>of()).stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
    }

    public String redact(String input) {
        if (input == null || input.isEmpty()) {
            return Objects.requireNonNullElse(input, "");
        }
        String result = input;
        for (String secret : secrets) {
            result = result.replace(secret, MASK);
        }
        result = TOKEN_PATTERNS.get(0).matcher(result).replaceAll("$1" + MASK);
        result = TOKEN_PATTERNS.get(1).matcher(result).replaceAll("$1" + MASK);
        result = TOKEN_PATTERNS.get(2).matcher(result).replaceAll(MASK);
        return result;
    }
}
