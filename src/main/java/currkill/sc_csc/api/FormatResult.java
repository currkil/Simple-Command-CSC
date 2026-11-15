package currkill.sc_csc.api;

import java.util.List;

/**
 * {@link currkill.sc_csc.Csc#format} 的结果：输出文本 + 全部诊断。
 *
 * <p>语言层不做 IO。是否把诊断写进聊天栏、游戏日志、指令日志，由调用方决定。
 */
public record FormatResult(String text, List<Diagnostic> diagnostics) {

    public FormatResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public static FormatResult of(String text, List<Diagnostic> diagnostics) {
        return new FormatResult(text, diagnostics);
    }

    public static FormatResult plain(String text) {
        return new FormatResult(text, List.of());
    }

    /** 是否存在严重错误（调用方据此提示玩家）。 */
    public boolean hasSevere() {
        for (Diagnostic d : diagnostics) {
            if (d.isSevere()) {
                return true;
            }
        }
        return false;
    }

    public List<Diagnostic> severe() {
        return diagnostics.stream().filter(Diagnostic::isSevere).toList();
    }

    public List<Diagnostic> conversions() {
        return diagnostics.stream().filter(d -> d.severity() == Severity.CONVERSION).toList();
    }
}
