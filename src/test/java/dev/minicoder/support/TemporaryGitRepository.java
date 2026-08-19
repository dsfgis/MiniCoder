package dev.minicoder.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 为测试创建并初始化隔离的临时 Git 仓库，提供可重复的提交与文件 fixture。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class TemporaryGitRepository {
    private TemporaryGitRepository() {}

    public static Path create(Path directory) throws IOException, InterruptedException {
        Files.createDirectories(directory);
        // 固定分支和本地测试身份，避免测试结果依赖开发者机器的全局 Git 配置。
        git(directory, "init", "-b", "main");
        git(directory, "config", "user.name", "Mini Coder Tests");
        git(directory, "config", "user.email", "tests@example.invalid");
        Files.writeString(directory.resolve("README.md"), "hello\n", StandardCharsets.UTF_8);
        git(directory, "add", "README.md");
        git(directory, "commit", "-m", "initial");
        return directory;
    }

    public static String git(Path directory, String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("Git failed: " + output);
        }
        return output;
    }
}
