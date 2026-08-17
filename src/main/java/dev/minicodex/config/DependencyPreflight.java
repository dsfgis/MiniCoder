package dev.minicodex.config;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class DependencyPreflight {
    private DependencyPreflight() {}

    public static List<String> check(String... executables) {
        List<String> missing = new ArrayList<>();
        for (String executable : executables) {
            if (!available(executable, Duration.ofSeconds(5))) {
                missing.add(executable);
            }
        }
        return missing;
    }

    private static boolean available(String executable, Duration timeout) {
        try {
            Process process = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            return finished && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
