package dev.minicoder.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandPolicyTest {
    @TempDir Path temp;

    @Test
    void classifiesAllowApprovalAndDeny() {
        CommandPolicy policy = new CommandPolicy(temp);
        assertEquals(RiskClassification.ALLOW, policy.classify("git", List.of("status")).classification());
        assertEquals(RiskClassification.ALLOW, policy.classify("mvnw.cmd", List.of("test")).classification());
        assertEquals(RiskClassification.REQUIRE_APPROVAL,
                policy.classify("curl", List.of("https://example.com")).classification());
        assertEquals(RiskClassification.DENY, policy.classify("rm", List.of("-rf", ".")).classification());
        assertEquals(RiskClassification.DENY, policy.classify("git", List.of("reset", "--hard")).classification());
        assertEquals(RiskClassification.DENY,
                policy.classify("java", List.of(temp.resolve("../escape").toAbsolutePath().toString())).classification());
        assertEquals(RiskClassification.REQUIRE_APPROVAL,
                policy.classify("python", List.of("-c", "print('x')")).classification());
        assertEquals(RiskClassification.REQUIRE_APPROVAL,
                policy.classify("npm", List.of("exec", "unknown-package")).classification());
        assertEquals(RiskClassification.DENY,
                policy.classifyExplicitShell("Write-Output", List.of("ok; Remove-Item file")).classification());
        assertEquals(RiskClassification.DENY,
                policy.classifyExplicitShell("Get-ChildItem", List.of("Env:")).classification());
    }
}
