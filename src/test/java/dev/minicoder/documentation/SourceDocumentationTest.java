package dev.minicoder.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动态检查全部 Java 源码的中文类型 Javadoc 与统一作者记录，防止后续新增文件遗漏说明。
 *
 * @author Self David (dsfgis@gmail.com)
 */
class SourceDocumentationTest {
    // 拆分校验常量，避免测试源码自身在 Javadoc 之外再出现一条完整作者记录。
    private static final String AUTHOR = "@author Self David " + "(dsfgis@gmail.com)";
    private static final Pattern PRIMARY_TYPE = Pattern.compile(
            "(?m)^(?:public\\s+)?(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)?"
                    + "(?:class|interface|record|enum)\\s+[A-Za-z_$][\\w$]*");
    private static final Pattern CHINESE = Pattern.compile("[\\u4e00-\\u9fff]");

    @Test
    void allJavaSourcesHaveChineseTypeJavadocAndOneAuthorTag() throws IOException {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        List<Path> sources = new ArrayList<>();
        collectJavaSources(projectRoot.resolve("src/main/java"), sources);
        collectJavaSources(projectRoot.resolve("src/test/java"), sources);
        assertFalse(sources.isEmpty(), "未找到 Java 源码，无法执行注释覆盖检查");

        List<String> violations = new ArrayList<>();
        for (Path source : sources.stream().sorted().toList()) {
            verifySource(projectRoot, source, violations);
        }
        assertTrue(violations.isEmpty(), () -> "源码注释覆盖不完整：\n" + String.join("\n", violations));
    }

    private static void collectJavaSources(Path root, List<Path> sources) throws IOException {
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(sources::add);
        }
    }

    private static void verifySource(Path projectRoot, Path source, List<String> violations) throws IOException {
        String content = Files.readString(source, StandardCharsets.UTF_8);
        String relative = projectRoot.relativize(source).toString().replace('\\', '/');
        if (occurrences(content, AUTHOR) != 1) {
            violations.add(relative + "：作者记录必须且只能出现一次");
        }

        Matcher declaration = PRIMARY_TYPE.matcher(content);
        if (!declaration.find()) {
            violations.add(relative + "：未找到主要顶层类型声明");
            return;
        }
        int commentStart = content.lastIndexOf("/**", declaration.start());
        int commentEnd = commentStart < 0 ? -1 : content.indexOf("*/", commentStart);
        if (commentStart < 0 || commentEnd < 0 || commentEnd > declaration.start()) {
            violations.add(relative + "：主要顶层类型前缺少 Javadoc");
            return;
        }
        String betweenCommentAndType = content.substring(commentEnd + 2, declaration.start()).strip();
        if (!betweenCommentAndType.isEmpty() && !betweenCommentAndType.startsWith("@")) {
            violations.add(relative + "：类型 Javadoc 未与主要顶层类型直接关联");
            return;
        }
        String javadoc = content.substring(commentStart, commentEnd + 2);
        if (!CHINESE.matcher(javadoc).find()) {
            violations.add(relative + "：类型 Javadoc 不含中文职责说明");
        }
        if (occurrences(javadoc, AUTHOR) != 1) {
            violations.add(relative + "：统一作者记录必须位于类型 Javadoc 内");
        }
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }
}
