package currkill.sc_csc.internal;

import currkill.sc_csc.api.CscContext;
import currkill.sc_csc.api.CscConversionException;
import currkill.sc_csc.api.CscDiagnostic;
import currkill.sc_csc.api.CscType;
import currkill.sc_csc.api.CscValue;

/**
 * 隐式转换的统一入口。
 *
 * <p>合法的转换一律记一条隐式转换诊断；非法的转换抛
 * {@link CscConversionException}，由调用方升级成严重错误。
 */
final class Conversions {

    private Conversions() {
    }

    /**
     * 把值转换成目标类型。字符串参与数值/布尔运算、布尔参与数值运算、浮点转整数都在这里记录。
     */
    static CscValue to(CscType target, CscValue value, CscContext ctx, int position, String where) {
        if (value.type() == target) {
            return value;
        }
        CscValue converted = convert(target, value);
        ctx.report(CscDiagnostic.conversion(
                "隐式转换 " + value.type().keyword() + " -> " + target.keyword() + "（" + where + "）", position));
        return converted;
    }

    private static CscValue convert(CscType target, CscValue value) {
        return switch (target) {
            case INT -> CscValue.ofInt(value.asInt());
            case FLOAT -> CscValue.ofFloat(value.asFloat());
            case BOOL -> CscValue.ofBool(value.asBool());
            case STRING -> CscValue.ofString(value.asString());
            default -> throw new CscConversionException(value.type(), target);
        };
    }

    /** 数值二元运算是否按浮点进行：任一侧是浮点，或任一侧字符串带小数点/指数。 */
    static boolean useFloat(CscValue left, CscValue right) {
        return left.type() == CscType.FLOAT || right.type() == CscType.FLOAT
                || looksFloat(left) || looksFloat(right);
    }

    private static boolean looksFloat(CscValue value) {
        if (value.type() != CscType.STRING) {
            return false;
        }
        String text = value.asString().trim();
        return text.contains(".") || text.contains("e") || text.contains("E");
    }

    /** 取整数（带隐式转换记录）。 */
    static long asInt(CscValue value, CscContext ctx, int position, String where) {
        if (value.type() == CscType.INT) {
            return value.asInt();
        }
        return to(CscType.INT, value, ctx, position, where).asInt();
    }

    /** 取浮点（带隐式转换记录）。 */
    static double asFloat(CscValue value, CscContext ctx, int position, String where) {
        if (value.type() == CscType.FLOAT) {
            return value.asFloat();
        }
        return to(CscType.FLOAT, value, ctx, position, where).asFloat();
    }

    /** 取布尔（带隐式转换记录）。 */
    static boolean asBool(CscValue value, CscContext ctx, int position, String where) {
        if (value.type() == CscType.BOOL) {
            return value.asBool();
        }
        return to(CscType.BOOL, value, ctx, position, where).asBool();
    }
}
