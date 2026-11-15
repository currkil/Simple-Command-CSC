package currkill.sc_csc.internal;

import currkill.sc_csc.api.EvaluationException;

import java.math.BigDecimal;

/**
 * 表达式值：弱类型，只有数值、字符串、布尔三种。
 *
 * <p>类型转换不在这里做，而是在求值器里做，因为只有求值器知道上下文并能记录隐式转换诊断。
 */
public final class Value {

    public enum Kind {
        NUMBER,
        STRING,
        BOOLEAN
    }

    private final Kind kind;
    private final double number;
    private final String text;
    private final boolean flag;

    private Value(Kind kind, double number, String text, boolean flag) {
        this.kind = kind;
        this.number = number;
        this.text = text;
        this.flag = flag;
    }

    public static Value number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new EvaluationException("表达式结果不是有效数值");
        }
        return new Value(Kind.NUMBER, value, null, false);
    }

    public static Value string(String value) {
        return new Value(Kind.STRING, 0.0D, value == null ? "" : value, false);
    }

    public static Value bool(boolean value) {
        return new Value(Kind.BOOLEAN, 0.0D, null, value);
    }

    public Kind kind() {
        return kind;
    }

    public boolean isString() {
        return kind == Kind.STRING;
    }

    public double numberValue() {
        return number;
    }

    public String stringValue() {
        return text;
    }

    public boolean booleanValue() {
        return flag;
    }

    /** 输出成文本时的样子。 */
    public String asText() {
        return switch (kind) {
            case NUMBER -> formatNumber(number);
            case STRING -> text;
            case BOOLEAN -> Boolean.toString(flag);
        };
    }

    /**
     * 数值输出：整数值不带小数点，其余用去尾的十进制表示（不用科学计数法，便于玩家阅读）。
     */
    public static String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new EvaluationException("表达式结果不是有效数值");
        }
        if (value == Math.rint(value) && Math.abs(value) < 9.007199254740992E15D) {
            return Long.toString((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    @Override
    public String toString() {
        return kind + "(" + asText() + ")";
    }
}
