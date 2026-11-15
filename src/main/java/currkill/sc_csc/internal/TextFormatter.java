package currkill.sc_csc.internal;

import currkill.sc_csc.api.Diagnostic;
import currkill.sc_csc.api.EvaluationException;
import currkill.sc_csc.api.FormatResult;
import currkill.sc_csc.api.VariableProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文本格式化器：把原始文本扫成「字面量 / 转义 / 注释 / 变量 / 表达式」的序列并输出结果。
 *
 * <p>容错粒度是「片段」：出错的片段原样保留在输出里，其他片段正常处理，同时记录诊断。
 */
public final class TextFormatter {

    private final String src;
    private final VariableProvider vars;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final StringBuilder out = new StringBuilder();
    private int pos;

    private TextFormatter(String src, VariableProvider vars) {
        this.src = src;
        this.vars = vars == null ? VariableProvider.empty() : vars;
    }

    public static FormatResult format(String raw, VariableProvider vars) {
        if (raw == null || raw.isEmpty()) {
            return FormatResult.of("", List.of());
        }
        TextFormatter formatter = new TextFormatter(raw, vars);
        formatter.scan();
        return FormatResult.of(formatter.out.toString(), formatter.diagnostics);
    }

    private void scan() {
        int length = src.length();
        while (pos < length) {
            char c = src.charAt(pos);
            switch (c) {
                case '\\' -> comment();
                case '/' -> slash();
                case '%' -> variable();
                default -> {
                    out.append(c);
                    pos++;
                }
            }
        }
    }

    /** 注释：\...\ ，不嵌套，不输出。未闭合时整段原样保留并报严重错误。 */
    private void comment() {
        int close = src.indexOf('\\', pos + 1);
        if (close < 0) {
            diagnostics.add(Diagnostic.severe("未闭合的注释，缺少结尾 \\", pos));
            out.append(src, pos, src.length());
            pos = src.length();
            return;
        }
        pos = close + 1;
    }

    /** 转义：/ 打头的两字符序列；/!( 表示表达式开始。 */
    private void slash() {
        int length = src.length();
        if (pos + 1 >= length) {
            diagnostics.add(Diagnostic.severe("转义符 / 后面没有字符", pos));
            out.append('/');
            pos++;
            return;
        }
        char next = src.charAt(pos + 1);
        if (next == '!' && pos + 2 < length && src.charAt(pos + 2) == '(') {
            expression();
            return;
        }
        switch (next) {
            case '/' -> out.append('/');
            case 'n' -> out.append('\n');
            case 't' -> out.append('\t');
            case 'r' -> out.append('\r');
            case '%' -> out.append('%');
            case '!' -> out.append('!');
            case '\\' -> out.append('\\');
            default -> {
                diagnostics.add(Diagnostic.severe("未知的转义序列 /" + next, pos));
                out.append('/').append(next);
            }
        }
        pos += 2;
    }

    /** 变量：%name% ，内部整体是变量名。未知变量报严重错误并原样保留。 */
    private void variable() {
        int length = src.length();
        int close = src.indexOf('%', pos + 1);
        if (close < 0) {
            diagnostics.add(Diagnostic.severe("变量缺少结尾 %", pos));
            out.append(src, pos, length);
            pos = length;
            return;
        }
        String name = src.substring(pos + 1, close);
        if (name.isEmpty()) {
            diagnostics.add(Diagnostic.severe("变量名为空", pos));
            out.append("%%");
            pos = close + 1;
            return;
        }
        Optional<String> value = vars.get(name);
        if (value.isPresent()) {
            out.append(value.get());
        } else {
            diagnostics.add(Diagnostic.severe("未知变量 %" + name + "%", pos));
            out.append('%').append(name).append('%');
        }
        pos = close + 1;
    }

    /** 表达式：/!( ... ) ，求出结果后输出结果文本；出错时整段原样保留。 */
    private void expression() {
        int start = pos;
        int bodyStart = pos + 3;
        int depth = 0;
        int i = pos + 2;
        int length = src.length();
        int closing = -1;
        while (i < length) {
            char c = src.charAt(i);
            if (c == '\'') {
                i++;
                while (i < length) {
                    char inner = src.charAt(i);
                    if (inner == '\\') {
                        i += 2;
                        continue;
                    }
                    if (inner == '\'') {
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
            diagnostics.add(Diagnostic.severe("未闭合的表达式，缺少右括号 )", start));
            out.append(src, start, length);
            pos = length;
            return;
        }
        String body = src.substring(bodyStart, closing);
        try {
            Value value = new ExpressionParser(body, vars, diagnostics, bodyStart).parse();
            out.append(value.asText());
        } catch (EvaluationException e) {
            diagnostics.add(Diagnostic.severe(e.getMessage(), start));
            out.append(src, start, closing + 1);
        }
        pos = closing + 1;
    }
}
