package dev.minicoder.tool.shell;

import dev.minicoder.agent.CancellationToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProcessRunner {
    public ProcessResult run(CommandSpec spec, Path workingDirectory, CancellationToken token) throws IOException {
        token.throwIfCancelled();
        List<String> command = buildCommand(spec);
        Process process = new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
        Instant started = Instant.now();
        BoundedCapture stdoutCapture = new BoundedCapture(spec.maxOutputBytes());
        BoundedCapture stderrCapture = new BoundedCapture(spec.maxOutputBytes());
        Thread stdout = Thread.startVirtualThread(() -> stdoutCapture.read(process.getInputStream()));
        Thread stderr = Thread.startVirtualThread(() -> stderrCapture.read(process.getErrorStream()));
        boolean timedOut = false;
        boolean finished;
        try {
            long remaining = spec.timeout().toMillis();
            while (!(finished = process.waitFor(Math.min(remaining, 100), TimeUnit.MILLISECONDS))) {
                if (token.isCancelled()) {
                    break;
                }
                remaining = spec.timeout().minus(Duration.between(started, Instant.now())).toMillis();
                if (remaining <= 0) {
                    timedOut = true;
                    break;
                }
            }
            if (!finished || timedOut || token.isCancelled()) {
                terminateTree(process);
            }
            stdout.join(2_000);
            stderr.join(2_000);
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            boolean cleaned = !process.isAlive() && process.descendants().noneMatch(ProcessHandle::isAlive);
            return new ProcessResult(exitCode, stdoutCapture.text(), stderrCapture.text(),
                    Duration.between(started, Instant.now()), timedOut, stdoutCapture.truncated() || stderrCapture.truncated(),
                    stdoutCapture.totalBytes(), stderrCapture.totalBytes(), cleaned);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminateTree(process);
            throw new IOException("Process interrupted", e);
        }
    }

    protected List<String> buildCommand(CommandSpec spec) {
        if (spec.shellMode() == ShellMode.NONE) {
            List<String> command = new ArrayList<>();
            command.add(spec.executable());
            command.addAll(spec.arguments());
            return command;
        }
        String expression = spec.executable();
        if (!spec.arguments().isEmpty()) {
            expression += " " + String.join(" ", spec.arguments());
        }
        return switch (spec.shellMode()) {
            case POWERSHELL -> List.of("powershell", "-NoProfile", "-NonInteractive", "-Command", expression);
            case CMD -> List.of("cmd", "/d", "/s", "/c", expression);
            case BASH -> List.of("bash", "-lc", expression);
            case NONE -> throw new IllegalStateException("Unexpected shell mode");
        };
    }

    private static void terminateTree(Process process) {
        process.descendants().forEach(handle -> {
            handle.destroy();
            if (handle.isAlive()) handle.destroyForcibly();
        });
        process.destroy();
        try {
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static final class BoundedCapture {
        private final int limit;
        private final ByteArrayOutputStream kept = new ByteArrayOutputStream();
        private long totalBytes;

        private BoundedCapture(int limit) { this.limit = limit; }

        private void read(InputStream input) {
            try (input) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    totalBytes += read;
                    int remaining = limit - kept.size();
                    if (remaining > 0) kept.write(buffer, 0, Math.min(remaining, read));
                }
            } catch (IOException ignored) {
            }
        }

        private String text() { return kept.toString(StandardCharsets.UTF_8); }
        private boolean truncated() { return totalBytes > limit; }
        private long totalBytes() { return totalBytes; }
    }
}
