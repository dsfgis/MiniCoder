package dev.minicoder.security;

/**
 * 表示命令策略的三类稳定裁决：允许、需要批准或拒绝。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public enum RiskClassification {
    ALLOW,
    REQUIRE_APPROVAL,
    DENY
}
