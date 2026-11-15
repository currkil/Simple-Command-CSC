package currkill.sc_csc.internal;

import currkill.sc_csc.api.Diagnostic;
import currkill.sc_csc.api.EvaluationException;
import currkill.sc_csc.api.VariableProvider;

import java.util.List;
import java.util.Optional;

/**
 * 表达式解析 + 求值（单遍递归下降）。
 *
 * <p>运算符优先级由低到高：{@code ||}、{@code ^}、{@code &&}、{@code + -}、
 * {@code * / /@}、一元 {@code + -}、{@code **}、基本项。
 *
 * <p>{@code **} 右结合，且比一元负号紧，因此 {@code -2**2 == -4}（与 Python 一致）。
 * {@code /@} 是取余。
 *
 * <p>求值器无副作用、可重入、不依赖 Minecraft 类型；变量只通过 {@link VariableProvider} 读取。
 * {@code &&} / {@code ||} 不做短路求值（语言层是纯求值，两侧都会算完），这一点写在 LANGUAGE.md。
 */
public final class ExpressionParser {

    private final String src;
    private final int baseOffset;
    private final VariableProvider vars;
    private final List<Diagnostic> diagnostics;
    private int pos;

    public ExpressionParser(String source, VariableProvider vars, List<Diagnostic> diagnostics, int baseOffset) {
        this.src = source;
        this.vars = vars == null ? VariableProvider.empty() : vars;
        this.diagnostics = diagnostics;
        this.baseOffset = baseOffset;
    }

    public Value parse() {
        if (src.isBlank()) {
            throw error("表达式为空");
        }
        Value value = parseOr();
        skipWhitespace();
        if (pos < src.length()) {
            throw error("表达式中多出无法识别的内容：" + src.substring(pos));
        }
        return value;
    }

    // ---------------------------------------------------------------- 语法层

    private Value parseOr() {
        Value left = parseXor();
        while (true) {
            skipWhitespace();
            if (!consume("||")) {
                return left;
            }
            Value right = parseXor();
            left = Value.bool(asBoolean(left, "||") || asBoolean(right, "||"));
        }
    }

    private Value parseXor() {
        Value left = parseAnd();
        while (true) {
            skipWhitespace();
            if (!consume("^")) {
                return left;
            }
            Value right = parseAnd();
            left = Value.bool(asBoolean(left, "^") ^ asBoolean(right, "^"));
        }
    }

    private Value parseAnd() {
        Value left = parseAdditive();
        while (true) {
            skipWhitespace();
            if (!consume("&&")) {
                return left;
            }
            Value right = parseAdditive();
            left = Value.bool(asBoolean(left, "&&") && asBoolean(right, "&&"));
        }
    }

    private Value parseAdditive() {
        Value left = parseMultiplicative();
        while (true) {
            skipWhitespace();
            if (consume("+")) {
                left = add(left, parseMultiplicative());
            } else if (consume("-")) {
                left = Value.number(asNumber(left, "-") - asNumber(parseMultiplicative(), "-"));
            } else {
                return left;
            }
        }
    }

    private Value parseMultiplicative() {
        Value left = parseUnary();
        while (true) {
            skipWhitespace();
            if (peek("**")) {
                return left;
            }
            if (consume("/@")) {
                left = remainder(left, parseUnary());
            } else if (consume("/")) {
                left = divide(left, parseUnary());
            } else if (consume("*")) {
                left = Value.number(asNumber(left, "*") * asNumber(parseUnary(), "*"));
            } else {
                return left;
            }
        }
    }

    private Value parseUnary() {
        skipWhitespace();
        if (consume("-")) {
            return Value.number(-asNumber(parseUnary(), "一元 -"));
        }
        if (consume("+")) {
            return Value.number(asNumber(parseUnary(), "一元 +"));
        }
        return parsePower();
    }

    private Value parsePower() {
        Value base = parsePrimary();
        skipWhitespace();
        if (consume("**")) {
            double exponent = asNumber(parseUnary(), "**");
            return Value.number(Math.pow(asNumber(base, "**"), exponent));
        }
        return base;
    }

    private Value parsePrimary() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw error("表达式意外结束");
        }
        char c = src.charAt(pos);
        if (c == '(') {
            pos++;
            Value inner = parseOr();
            skipWhitespace();
            if (!consume(")")) {
                throw error("缺少右括号 )");
            }
            return inner;
        }
        if (c == '\'') {
            return parseStringLiteral();
        }
        if (c == '%') {
            return parseVariable();
        }
        if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
            return parseNumberLiteral();
        }
        if (Character.isLetter(c) || c == '_') {
            return parseWord();
        }
        throw error("无法识别的记号 '" + c + "'");
    }

    // ---------------------------------------------------------------- 字面量

    private Value parseNumberLiteral() {
        int start = pos;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
            pos++;
        }
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            int mark = pos;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            if (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            } else {
                pos = mark;
            }
        }
        String literal = src.substring(start, pos);
        try {
            return Value.number(Double.parseDouble(literal));
        } catch (NumberFormatException e) {
            throw error("无效的数字：" + literal);
        }
    }

    private Value parseStringLiteral() {
        pos++; // 跳过开头的 '
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\\') {
                if (pos + 1 >= src.length()) {
                    throw error("字符串中缺少转义字符");
                }
                char n = src.charAt(pos + 1);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    case '\'' -> sb.append('\'');
                    case '"' -> sb.append('"');
                    case '%' -> sb.append('%');
                    default -> throw error("字符串中未知的转义序列 \\" + n);
                }
                pos += 2;
                continue;
            }
            if (c == '\'') {
                pos++;
                return Value.string(sb.toString());
            }
            sb.append(c);
            pos++;
        }
        throw error("字符串缺少结尾的单引号 '");
    }

    private Value parseVariable() {
        int start = pos;
        pos++; // 跳过开头的 %
        int close = src.indexOf('%', pos);
        if (close < 0) {
            throw error("变量缺少结尾 %");
        }
        String name = src.substring(pos, close);
        pos = close + 1;
        if (name.isEmpty()) {
            throw error("变量名为空");
        }
        Optional<String> value = vars.get(name);
        if (value.isEmpty()) {
            throw new EvaluationException("未知变量 %" + name + "%");
        }
        // 变量对外一律是字符串；参与运算时按运算符需要再做隐式转换。
        return Value.string(value.get());
    }

    private Value parseWord() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_') {
                pos++;
            } else {
                break;
            }
        }
        String word = src.substring(start, pos);
        if ("true".equalsIgnoreCase(word)) {
            return Value.bool(true);
        }
        if ("false".equalsIgnoreCase(word)) {
            return Value.bool(false);
        }
        throw error("未知的标识符：" + word);
    }

    // ---------------------------------------------------------------- 运算

    private Value add(Value left, Value right) {
        if (left.isString() || right.isString()) {
            if (!left.isString() || !right.isString()) {
                conversion("拼接时把非字符串值隐式转换为字符串", -1);
            }
            return Value.string(left.asText() + right.asText());
        }
        return Value.number(asNumber(left, "+") + asNumber(right, "+"));
    }

    private Value divide(Value left, Value right) {
        double divisor = asNumber(right, "/");
        if (divisor == 0.0D) {
            throw new EvaluationException("除零");
        }
        return Value.number(asNumber(left, "/") / divisor);
    }

    private Value remainder(Value left, Value right) {
        double divisor = asNumber(right, "/@");
        if (divisor == 0.0D) {
            throw new EvaluationException("取余时除零");
        }
        return Value.number(asNumber(left, "/@") % divisor);
    }

    private double asNumber(Value value, String operator) {
        switch (value.kind()) {
            case NUMBER:
                return value.numberValue();
            case BOOLEAN:
                conversion("布尔值在 " + operator + " 中按数值处理", -1);
                return value.booleanValue() ? 1.0D : 0.0D;
            case STRING:
            default:
                String text = value.stringValue().trim();
                try {
                    double parsed = Double.parseDouble(text);
                    conversion("字符串 \"" + text + "\" 在 " + operator + " 中被解析为数值", -1);
                    return parsed;
                } catch (NumberFormatException e) {
                    throw new EvaluationException("无法把 \"" + value.stringValue() + "\" 当作数值参与 " + operator);
                }
        }
    }

    private boolean asBoolean(Value value, String operator) {
        switch (value.kind()) {
            case BOOLEAN:
                return value.booleanValue();
            case NUMBER:
                conversion("数值在 " + operator + " 中按布尔处理", -1);
                return value.numberValue() != 0.0D;
            case STRING:
            default:
                String text = value.stringValue().trim();
                if ("true".equalsIgnoreCase(text)) {
                    conversion("字符串 \"" + text + "\" 在 " + operator + " 中被解析为布尔", -1);
                    return true;
                }
                if ("false".equalsIgnoreCase(text)) {
                    conversion("字符串 \"" + text + "\" 在 " + operator + " 中被解析为布尔", -1);
                    return false;
                }
                throw new EvaluationException("无法把 \"" + value.stringValue() + "\" 当作布尔参与 " + operator);
        }
    }

    // ---------------------------------------------------------------- 工具

    private void conversion(String message, int localPosition) {
        diagnostics.add(Diagnostic.conversion(message, baseOffset + (localPosition < 0 ? pos : localPosition)));
    }

    private EvaluationException error(String message) {
        return new EvaluationException(message + "（偏移 " + (baseOffset + pos) + "）");
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private boolean peek(String token) {
        return src.startsWith(token, pos);
    }

    private boolean consume(String token) {
        if (src.startsWith(token, pos)) {
            pos += token.length();
            return true;
        }
        return false;
    }
}
