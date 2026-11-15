package currkill.sc_csc.api;

/**
 * 一条格式化诊断。
 *
 * @param severity 级别
 * @param message  人类可读的说明
 * @param position 在原始文本中的字符偏移，-1 表示不属于具体文本片段
 */
public record Diagnostic(Severity severity, String message, int position) {

    public static Diagnostic severe(String message, int position) {
        return new Diagnostic(Severity.SEVERE, message, position);
    }

    public static Diagnostic conversion(String message, int position) {
        return new Diagnostic(Severity.CONVERSION, message, position);
    }

    public boolean isSevere() {
        return severity == Severity.SEVERE;
    }

    @Override
    public String toString() {
        return isSevere() ? "[严重@" + position + "] " + message : "[隐式转换@" + position + "] " + message;
    }
}
