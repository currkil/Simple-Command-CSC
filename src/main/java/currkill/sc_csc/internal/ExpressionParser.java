package currkill.sc_csc.internal;

import currkill.sc_csc.api.CscContext;
import currkill.sc_csc.api.CscDiagnostic;
import currkill.sc_csc.api.CscRuntimeException;
import currkill.sc_csc.api.CscSyntaxException;
import currkill.sc_csc.api.CscType;
import currkill.sc_csc.api.CscValue;

import java.util.List;

/**
 * 表达式解析与求值（单遍递归下降，边解析边算）。
 *
 * <p>优先级由低到高：{@code ^}、{@code ||}、{@code &&}、{@code == !=}、
 * {@code < <= > >=}、{@code + -}、{@code * / %mod}、一元 {@code + - !}、
 * {@code **}（右结合）、基本项与下标。
 *
 * <p>{@code %mod} 与 {@code %变量%} 按上下文区分：在“该出现运算符”的位置遇到
 * {@code %mod} 且后面不是 {@code %}，就是取模；在“该出现操作数”的位置遇到 {@code %}
 * 就按变量配对到下一个 {@code %}。
 *
 * <p>不求值副作用、不做 IO；转换记录通过 {@link CscContext#report} 交回宿主。
 */
public final class ExpressionParser {

    private final String src;
    private final CscContext ctx;
    private final int baseOffset;
    private int pos;

    public ExpressionParser(String source, CscContext ctx, int baseOffset) {
        this.src = source == null ? "" : source;
        this.ctx = ctx;
        this.baseOffset = baseOffset;
    }

    /** 求值整段表达式文本。 */
    public static CscValue parseFull(String source, CscContext ctx, int baseOffset) {
        return new ExpressionParser(source, ctx, baseOffset).parse();
    }

    public CscValue parse() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw new CscSyntaxException("表达式为空", baseOffset);
        }
        CscValue value = parseOr();
        skipWhitespace();
        if (pos < src.length()) {
            throw new CscSyntaxException("表达式里多出无法识别的内容：" + src.substring(pos), absolute());
        }
        return value;
    }

    // ---------------------------------------------------------------- 优先级层

    private CscValue parseOr() {
        CscValue left = parseXor();
        while (true) {
            skipWhitespace();
            if (!consume("||")) {
                return left;
            }
            boolean l = Conversions.asBool(left, ctx, absolute(), "||");
            boolean r = Conversions.asBool(parseXor(), ctx, absolute(), "||");
            left = CscValue.ofBool(l || r);
        }
    }

    private CscValue parseXor() {
        CscValue left = parseAnd();
        while (true) {
            skipWhitespace();
            if (!consume("^")) {
                return left;
            }
            long l = Conversions.asInt(left, ctx, absolute(), "^");
            long r = Conversions.asInt(parseAnd(), ctx, absolute(), "^");
            left = CscValue.ofInt(l ^ r);
        }
    }

    private CscValue parseAnd() {
        CscValue left = parseEquality();
        while (true) {
            skipWhitespace();
            if (!consume("&&")) {
                return left;
            }
            boolean l = Conversions.asBool(left, ctx, absolute(), "&&");
            boolean r = Conversions.asBool(parseEquality(), ctx, absolute(), "&&");
            left = CscValue.ofBool(l && r);
        }
    }

    private CscValue parseEquality() {
        CscValue left = parseComparison();
        while (true) {
            skipWhitespace();
            String op;
            if (consume("==")) {
                op = "==";
            } else if (consume("!=")) {
                op = "!=";
            } else {
                return left;
            }
            CscValue right = parseComparison();
            boolean equal = valuesEqual(left, right);
            left = CscValue.ofBool("==".equals(op) == equal);
        }
    }

    private CscValue parseComparison() {
        CscValue left = parseAdditive();
        while (true) {
            skipWhitespace();
            String op = null;
            if (consume("<=")) {
                op = "<=";
            } else if (consume(">=")) {
                op = ">=";
            } else if (consume("<")) {
                op = "<";
            } else if (consume(">")) {
                op = ">";
            }
            if (op == null) {
                return left;
            }
            CscValue right = parseAdditive();
            int cmp = compare(left, right);
            left = CscValue.ofBool(switch (op) {
                case "<=" -> cmp <= 0;
                case ">=" -> cmp >= 0;
                case "<" -> cmp < 0;
                default -> cmp > 0;
            });
        }
    }

    private CscValue parseAdditive() {
        CscValue left = parseMultiplicative();
        while (true) {
            skipWhitespace();
            if (consume("+")) {
                left = add(left, parseMultiplicative());
            } else if (consume("-")) {
                left = subtract(left, parseMultiplicative());
            } else {
                return left;
            }
        }
    }

    private CscValue parseMultiplicative() {
        CscValue left = parseUnary();
        while (true) {
            skipWhitespace();
            if (src.startsWith("**", pos)) {
                return left;
            }
            if (tryModKeyword()) {
                left = modulo(left, parseUnary());
            } else if (consume("/")) {
                left = divide(left, parseUnary());
            } else if (consume("*")) {
                left = multiply(left, parseUnary());
            } else {
                return left;
            }
        }
    }

    private CscValue parseUnary() {
        skipWhitespace();
        if (consume("-")) {
            CscValue operand = parseUnary();
            return Conversions.useFloat(operand, operand)
                    ? CscValue.ofFloat(-Conversions.asFloat(operand, ctx, absolute(), "一元 -"))
                    : CscValue.ofInt(-Conversions.asInt(operand, ctx, absolute(), "一元 -"));
        }
        if (consume("+")) {
            CscValue operand = parseUnary();
            return Conversions.useFloat(operand, operand)
                    ? CscValue.ofFloat(Conversions.asFloat(operand, ctx, absolute(), "一元 +"))
                    : CscValue.ofInt(Conversions.asInt(operand, ctx, absolute(), "一元 +"));
        }
        if (consume("!")) {
            return CscValue.ofBool(!Conversions.asBool(parseUnary(), ctx, absolute(), "!"));
        }
        return parsePower();
    }

    private CscValue parsePower() {
        CscValue base = parsePrimary();
        skipWhitespace();
        if (!consume("**")) {
            return base;
        }
        CscValue exponent = parseUnary();
        boolean useFloat = Conversions.useFloat(base, exponent);
        if (!useFloat) {
            long b = Conversions.asInt(base, ctx, absolute(), "**");
            long e = Conversions.asInt(exponent, ctx, absolute(), "**");
            if (e >= 0) {
                double result = Math.pow(b, e);
                if (result > Long.MAX_VALUE || result < Long.MIN_VALUE) {
                    throw new CscRuntimeException("整数幂结果越界");
                }
                return CscValue.ofInt((long) result);
            }
        }
        double result = Math.pow(Conversions.asFloat(base, ctx, absolute(), "**"),
                Conversions.asFloat(exponent, ctx, absolute(), "**"));
        return CscValue.ofFloat(result);
    }

    private CscValue parsePrimary() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw new CscSyntaxException("表达式意外结束", absolute());
        }
        char c = src.charAt(pos);
        if (c == '(') {
            pos++;
            CscValue inner = parseOr();
            skipWhitespace();
            if (!consume(")")) {
                throw new CscSyntaxException("缺少右括号 )", absolute());
            }
            return inner;
        }
        if (c == '"') {
            return parseStringLiteral();
        }
        if (c == '%') {
            return parseVariable();
        }
        if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
            return parseNumberLiteral();
        }
        if (Character.isLetter(c)) {
            int start = pos;
            while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
                pos++;
            }
            String word = src.substring(start, pos);
            if ("true".equals(word)) {
                return CscValue.ofBool(true);
            }
            if ("false".equals(word)) {
                return CscValue.ofBool(false);
            }
            throw new CscSyntaxException("未知的标识符：" + word + "（变量请写成 %" + word + "%）", absolute());
        }
        throw new CscSyntaxException("无法识别的记号 '" + c + "'", absolute());
    }

    // ---------------------------------------------------------------- 基本项

    private CscValue parseNumberLiteral() {
        int start = pos;
        boolean isFloat = false;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
            pos++;
        }
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloat = true;
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
                isFloat = true;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            } else {
                pos = mark;
            }
        }
        String literal = src.substring(start, pos);
        try {
            return isFloat ? CscValue.ofFloat(Double.parseDouble(literal)) : CscValue.ofInt(Long.parseLong(literal));
        } catch (NumberFormatException e) {
            throw new CscSyntaxException("无效的数字：" + literal, absolute());
        }
    }

    private CscValue parseStringLiteral() {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\\') {
                if (pos + 1 >= src.length()) {
                    throw new CscSyntaxException("字符串里缺少转义字符", absolute());
                }
                char n = src.charAt(pos + 1);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '%' -> sb.append('%');
                    default -> throw new CscSyntaxException("字符串里未知的转义序列 \\" + n, absolute());
                }
                pos += 2;
                continue;
            }
            if (c == '"') {
                pos++;
                return CscValue.ofString(sb.toString());
            }
            sb.append(c);
            pos++;
        }
        throw new CscSyntaxException("字符串缺少结尾的双引号", absolute());
    }

    private CscValue parseVariable() {
        int start = pos;
        pos++;
        int close = src.indexOf('%', pos);
        if (close < 0) {
            throw new CscSyntaxException("变量缺少结尾 %", absolute());
        }
        String name = src.substring(pos, close).trim();
        pos = close + 1;
        if (name.isEmpty()) {
            throw new CscSyntaxException("变量名为空", baseOffset + start);
        }
        if (!ctx.hasVariable(name)) {
            throw new CscRuntimeException("未知变量 %" + name + "%");
        }
        CscValue value = ctx.getVariable(name);
        return parseIndex(value, name);
    }

    /** 下标：{@code %list%[i]}，{@code %list%[size]} 返回元素个数。 */
    private CscValue parseIndex(CscValue value, String name) {
        skipWhitespace();
        if (pos >= src.length() || src.charAt(pos) != '[') {
            return value;
        }
        pos++;
        skipWhitespace();
        int indexStart = pos;
        if (src.startsWith("size", pos)) {
            int after = pos + 4;
            if (after <= src.length() && (after == src.length() || src.charAt(after) == ']'
                    || Character.isWhitespace(src.charAt(after)))) {
                pos = after;
                skipWhitespace();
                if (!consume("]")) {
                    throw new CscSyntaxException("下标缺少右方括号 ]", absolute());
                }
                return CscValue.ofInt(value.size() < 0 ? 0 : value.size());
            }
        }
        long index = Conversions.asInt(parseOr(), ctx, indexStart + baseOffset, "下标");
        skipWhitespace();
        if (!consume("]")) {
            throw new CscSyntaxException("下标缺少右方括号 ]", absolute());
        }
        if (value.type() != CscType.LIST) {
            throw new CscRuntimeException("%" + name + "% 不是列表，不能下标访问");
        }
        List<CscValue> list = value.asList();
        if (index < 0 || index >= list.size()) {
            throw new CscRuntimeException("下标越界：%" + name + "%[" + index + "]，长度 " + list.size());
        }
        return list.get((int) index);
    }

    // ---------------------------------------------------------------- 运算

    private CscValue add(CscValue left, CscValue right) {
        if (left.type() == CscType.STRING || right.type() == CscType.STRING) {
            String l = asText(left, "+");
            String r = asText(right, "+");
            return CscValue.ofString(l + r);
        }
        return numeric("+", left, right);
    }

    private CscValue subtract(CscValue left, CscValue right) {
        return numeric("-", left, right);
    }

    private CscValue multiply(CscValue left, CscValue right) {
        return numeric("*", left, right);
    }

    private CscValue divide(CscValue left, CscValue right) {
        if (Conversions.useFloat(left, right)) {
            double divisor = Conversions.asFloat(right, ctx, absolute(), "/");
            if (divisor == 0.0D) {
                throw new CscRuntimeException("除零");
            }
            return CscValue.ofFloat(Conversions.asFloat(left, ctx, absolute(), "/") / divisor);
        }
        long divisor = Conversions.asInt(right, ctx, absolute(), "/");
        if (divisor == 0L) {
            throw new CscRuntimeException("除零");
        }
        return CscValue.ofInt(Conversions.asInt(left, ctx, absolute(), "/") / divisor);
    }

    private CscValue modulo(CscValue left, CscValue right) {
        if (Conversions.useFloat(left, right)) {
            double divisor = Conversions.asFloat(right, ctx, absolute(), "%mod");
            if (divisor == 0.0D) {
                throw new CscRuntimeException("%mod 除零");
            }
            return CscValue.ofFloat(Conversions.asFloat(left, ctx, absolute(), "%mod") % divisor);
        }
        long divisor = Conversions.asInt(right, ctx, absolute(), "%mod");
        if (divisor == 0L) {
            throw new CscRuntimeException("%mod 除零");
        }
        return CscValue.ofInt(Conversions.asInt(left, ctx, absolute(), "%mod") % divisor);
    }

    private CscValue numeric(String op, CscValue left, CscValue right) {
        if (Conversions.useFloat(left, right)) {
            double l = Conversions.asFloat(left, ctx, absolute(), op);
            double r = Conversions.asFloat(right, ctx, absolute(), op);
            return CscValue.ofFloat(switch (op) {
                case "+" -> l + r;
                case "-" -> l - r;
                default -> l * r;
            });
        }
        long l = Conversions.asInt(left, ctx, absolute(), op);
        long r = Conversions.asInt(right, ctx, absolute(), op);
        return CscValue.ofInt(switch (op) {
            case "+" -> l + r;
            case "-" -> l - r;
            default -> l * r;
        });
    }

    private String asText(CscValue value, String op) {
        if (value.type() == CscType.STRING) {
            return value.asString();
        }
        return Conversions.to(CscType.STRING, value, ctx, absolute(), "拼接 " + op).asString();
    }

    private boolean valuesEqual(CscValue left, CscValue right) {
        boolean leftNumberish = isNumberish(left);
        boolean rightNumberish = isNumberish(right);
        if (leftNumberish && rightNumberish) {
            return Conversions.useFloat(left, right)
                    ? Conversions.asFloat(left, ctx, absolute(), "==") == Conversions.asFloat(right, ctx, absolute(), "==")
                    : Conversions.asInt(left, ctx, absolute(), "==") == Conversions.asInt(right, ctx, absolute(), "==");
        }
        if (left.type() == CscType.BOOL && right.type() == CscType.BOOL) {
            return left.asBool() == right.asBool();
        }
        if (left.type() == CscType.STRING || right.type() == CscType.STRING) {
            return asText(left, "==").equals(asText(right, "=="));
        }
        throw new CscRuntimeException("无法比较 " + left.type().keyword() + " 和 " + right.type().keyword());
    }

    private int compare(CscValue left, CscValue right) {
        if (isNumberish(left) && isNumberish(right)) {
            if (Conversions.useFloat(left, right)) {
                return Double.compare(Conversions.asFloat(left, ctx, absolute(), "比较"),
                        Conversions.asFloat(right, ctx, absolute(), "比较"));
            }
            return Long.compare(Conversions.asInt(left, ctx, absolute(), "比较"),
                    Conversions.asInt(right, ctx, absolute(), "比较"));
        }
        if (left.type() == CscType.STRING || right.type() == CscType.STRING) {
            return asText(left, "比较").compareTo(asText(right, "比较"));
        }
        throw new CscRuntimeException("无法比较 " + left.type().keyword() + " 和 " + right.type().keyword());
    }

    private boolean isNumberish(CscValue value) {
        if (value.type() == CscType.INT || value.type() == CscType.FLOAT || value.type() == CscType.BOOL) {
            return true;
        }
        if (value.type() != CscType.STRING) {
            return false;
        }
        try {
            Double.parseDouble(value.asString().trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 在运算符位置识别 {@code %mod}；{@code %mod%} 是变量，不在此列。 */
    private boolean tryModKeyword() {
        if (!src.startsWith("%mod", pos)) {
            return false;
        }
        int after = pos + 4;
        if (after < src.length() && src.charAt(after) == '%') {
            return false;
        }
        pos = after;
        return true;
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private boolean consume(String token) {
        if (src.startsWith(token, pos)) {
            pos += token.length();
            return true;
        }
        return false;
    }

    private int absolute() {
        return baseOffset + pos;
    }

    /** 供宿主把分类诊断附到正确位置。 */
    public void reportConversion(String message) {
        ctx.report(CscDiagnostic.conversion(message, absolute()));
    }
}
