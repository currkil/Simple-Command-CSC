package currkill.sc_csc.internal;

import currkill.sc_csc.api.CscContext;
import currkill.sc_csc.api.CscDiagnostic;
import currkill.sc_csc.api.CscRuntimeException;
import currkill.sc_csc.api.CscValue;

/**
 * 文本模式：把一段带转义、注释、变量、表达式的文本展开成字符串。
 *
 * <p>容错粒度是片段：出错的片段原样保留，其余片段正常输出，同时把诊断交回宿主。
 */
public final class TextFormatter {

    private final String src;
    private final CscContext ctx;
    private final StringBuilder out = new StringBuilder();
    private int pos;

    public TextFormatter(String src, CscContext ctx) {
        this.src = src == null ? "" : src;
        this.ctx = ctx;
    }

    public static String format(String src, CscContext ctx) {
        if (src == null || src.isEmpty()) {
            return "";
        }
        TextFormatter formatter = new TextFormatter(src, ctx);
        formatter.scan();
        return formatter.out.toString();
    }

    private void scan() {
        int length = src.length();
        while (pos < length) {
            char c = src.charAt(pos);
            if (c == '\\') {
                escape();
            } else if (c == '/' && pos + 1 < length && (src.charAt(pos + 1) == '/' || src.charAt(pos + 1) == '*')) {
                comment();
            } else if (c == '%') {
                variable();
            } else {
                out.append(c);
                pos++;
            }
        }
    }

    private void escape() {
        int length = src.length();
        if (pos + 1 >= length) {
            out.append('\\');
            pos++;
            return;
        }
        char next = src.charAt(pos + 1);
        if (next == '!' && pos + 2 < length && src.charAt(pos + 2) == '(') {
            expression();
            return;
        }
        switch (next) {
            case '\\' -> out.append('\\');
            case 'n' -> out.append('\n');
            case 't' -> out.append('\t');
            case 'r' -> out.append('\r');
            case '%' -> out.append('%');
            case '!' -> out.append('!');
            default -> {
                // 规范：\ 后跟非转义字符时，\ 与后一个字符都按字面输出
                out.append('\\').append(next);
            }
        }
        pos += 2;
    }

    private void comment() {
        int length = src.length();
        char kind = src.charAt(pos + 1);
        if (kind == '/') {
            int end = src.indexOf('\n', pos + 2);
            pos = end < 0 ? length : end;
            return;
        }
        int end = src.indexOf("*/", pos + 2);
        if (end < 0) {
            ctx.report(CscDiagnostic.severe("未闭合的块注释 /*", pos));
            out.append(src, pos, length);
            pos = length;
            return;
        }
        pos = end + 2;
    }

    private void variable() {
        int length = src.length();
        int close = src.indexOf('%', pos + 1);
        if (close < 0) {
            // 规范：未闭合 % 按普通字符输出
            out.append('%');
            pos++;
            return;
        }
        String name = src.substring(pos + 1, close).trim();
        if (name.isEmpty()) {
            out.append("%%");
            pos = close + 1;
            return;
        }
        if (!ctx.hasVariable(name)) {
            ctx.report(CscDiagnostic.severe("未知变量 %" + name + "%", pos));
            out.append('%').append(src, pos + 1, close).append('%');
            pos = close + 1;
            return;
        }
        CscValue value = ctx.getVariable(name);
        out.append(value == null ? "" : value.asString());
        pos = close + 1;
    }

    private void expression() {
        int start = pos;
        int bodyStart = pos + 3;
        int depth = 0;
        int i = pos + 2;
        int length = src.length();
        int closing = -1;
        while (i < length) {
            char c = src.charAt(i);
            if (c == '"') {
                i++;
                while (i < length) {
                    char inner = src.charAt(i);
                    if (inner == '\\') {
                        i += 2;
                        continue;
                    }
                    if (inner == '"') {
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == '%') {
                int end = src.indexOf('%', i + 1);
                if (end > 0) {
                    i = end + 1;
                    continue;
                }
            }
            if (c == '(') {
                depth++;
                i++;
                continue;
            }
            if (c == ')') {
                depth--;
                if (depth == 0) {
                    closing = i;
                    break;
                }
                i++;
                continue;
            }
            i++;
        }
        if (closing < 0) {
            ctx.report(CscDiagnostic.severe("未闭合的表达式 \\!(，缺少右括号 )", start));
            out.append(src, start, length);
            pos = length;
            return;
        }
        String body = src.substring(bodyStart, closing);
        try {
            out.append(ExpressionParser.parseFull(body, ctx, bodyStart).asString());
        } catch (CscRuntimeException e) {
            ctx.report(CscDiagnostic.severe(e.getMessage(), start));
            out.append(src, start, closing + 1);
        }
        pos = closing + 1;
    }
}
