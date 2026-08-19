package dev.minicoder.tool.shell;

/**
 * 声明命令是否直接执行，或显式交由 PowerShell、cmd、bash 解释。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public enum ShellMode {
    NONE,
    POWERSHELL,
    CMD,
    BASH
}
