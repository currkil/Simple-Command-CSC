package currkill.sc_csc.api;

/**
 * 一条诊断。
 *
 * @param severity 级别
 * @param message  人类可读的说明
 * @param position 原始文本中的字符偏移，-1 表示不属于具体文本片段
 */
public record CscDiagnostic(CscSeverity severity, String message, int position) {

    public static CscDiagnostic severe(String message, int position) {
        return new CscDiagnostic(CscSeverity.SEVERE, message, position);
    }

    public static CscDiagnostic conversion(String message, int position) {
        return new CscDiagnostic(CscSeverity.CONVERSION, message, position);
    }

    public boolean isSevere() {
        return severity == CscSeverity.SEVERE;
    }

    @Override
    public String toString() {
        return (isSevere() ? "[严重@" : "[隐式转换@") + position + "] " + message;
    }
}
