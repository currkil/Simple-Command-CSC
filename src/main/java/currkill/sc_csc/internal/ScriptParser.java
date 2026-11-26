package currkill.sc_csc.internal;

import currkill.sc_csc.api.CscSyntaxException;
import currkill.sc_csc.api.CscType;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本解析器：逐行、语句级解析。
 *
 * <p>约定：
 * <ul>
 *   <li>普通语句以 {@code ;} 结束（可省略），块开始行以 {@code :} 结束；</li>
 *   <li>块结束关键字：{@code ctrl endif;}、{@code ctrl endfor;}、{@code ctrl endwhile;}、
 *       {@code ctrl endfunc;}，{@code ctrl do:} 以 {@code ctrl dwhile(...)} 结束；</li>
 *   <li>{@code ctrl endfunc with <表达式>;} 是带返回值的 return，可以出现在函数体内任意位置；
 *       函数体必须用 {@code ctrl endfunc;} 收尾。</li>
 * </ul>
 */
public final class ScriptParser {

    private final String[] lines;
    private int index;
    private int currentLineNo;

    private ScriptParser(String[] lines) {
        this.lines = lines;
    }

    public static List<Stmt> parse(String script) {
        String cleaned = stripComments(script == null ? "" : script);
        String[] raw = cleaned.split("\n", -1);
        ScriptParser parser = new ScriptParser(raw);
        List<Stmt> program = parser.parseBlock();
        while (parser.index < raw.length && raw[parser.index].isBlank()) {
            parser.index++;
        }
        if (parser.index < raw.length) {
            throw new CscSyntaxException("多余的块结束语句：" + raw[parser.index].trim()
                    + "（第 " + (parser.index + 1) + " 行）");
        }
        return program;
    }

    // ---------------------------------------------------------------- 块

    private List<Stmt> parseBlock() {
        List<Stmt> statements = new ArrayList<>();
        while (index < lines.length) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                index++;
                continue;
            }
            if (isTerminator(line)) {
                break;
            }
            currentLineNo = index + 1;
            index++;
            statements.add(parseLine(line));
        }
        return statements;
    }

    private static boolean isTerminator(String line) {
        return line.startsWith("ctrl endif")
                || line.startsWith("ctrl else")
                || line.startsWith("ctrl endfor")
                || line.startsWith("ctrl endwhile")
                || line.startsWith("ctrl dwhile")
                || isEndfunc(line);
    }

    /** {@code ctrl endfunc;} 是结束符；{@code ctrl endfunc with x;} 是 return。 */
    private static boolean isEndfunc(String line) {
        return line.startsWith("ctrl endfunc") && !line.matches("ctrl\\s+endfunc\\s+with\\b.*");
    }

    private void expect(String prefix, String hint) {
        if (index >= lines.length) {
            throw new CscSyntaxException("缺少 " + hint + "（第 " + currentLineNo + " 行起的块未闭合）");
        }
        String line = lines[index].trim();
        if (!line.startsWith(prefix)) {
            throw new CscSyntaxException("期望 " + hint + "，实际是：" + line + "（第 " + (index + 1) + " 行）");
        }
        index++;
    }

    // ---------------------------------------------------------------- 语句分发

    private Stmt parseLine(String line) {
        if (line.startsWith("ctrl ")) {
            return parseCtrl(line);
        }
        if (line.startsWith("var ")) {
            return parseVar(line);
        }
        if (line.startsWith("list ")) {
            return parseList(line);
        }
        if (line.startsWith("io ")) {
            return parseIo(line);
        }
        if (line.startsWith("log ")) {
            return parseLog(line);
        }
        if (line.startsWith("file ")) {
            return parseFile(line);
        }
        if (line.startsWith("sys ")) {
            return parseSys(line);
        }
        throw error("无法识别的语句：" + line);
    }

    private Stmt parseVar(String line) {
        Cursor cursor = new Cursor(stripSemicolon(line.substring(4)));
        String sub = cursor.word();
        switch (sub) {
            case "new" -> {
                String name = cursor.word();
                String typeText = cursor.word();
                String expr = cursor.rest();
                requireName(name);
                requireExpr(expr, "var new");
                return new Stmt.VarNew(name, parseType(typeText, true), expr);
            }
            case "set" -> {
                String name = cursor.word();
                String expr = cursor.rest();
                requireName(name);
                requireExpr(expr, "var set");
                return new Stmt.VarSet(name, expr);
            }
            case "delete" -> {
                String name = cursor.word();
                requireName(name);
                return new Stmt.VarDelete(name);
            }
            default -> throw error("var 只支持 new / set / delete，实际是：" + sub);
        }
    }

    private Stmt parseList(String line) {
        Cursor cursor = new Cursor(stripSemicolon(line.substring(5)));
        String sub = cursor.word();
        switch (sub) {
            case "new" -> {
                String name = cursor.word();
                String typeText = cursor.word();
                String tail = cursor.rest();
                requireName(name);
                if (tail.isBlank()) {
                    throw error("list new 缺少初始值");
                }
                List<String> exprs = new ArrayList<>();
                Integer declaredSize = null;
                if (tail.startsWith("[")) {
                    int close = matching(tail, 0, '[', ']');
                    if (close < 0) {
                        throw error("list new 的初始值列表缺少 ]");
                    }
                    String inside = tail.substring(1, close).trim();
                    if (!inside.isEmpty()) {
                        exprs.addAll(splitTopLevel(inside, ','));
                    }
                    String after = tail.substring(close + 1).trim();
                    if (!after.isEmpty()) {
                        declaredSize = parseInt(after);
                    }
                } else {
                    String[] tokens = tail.split("\\s+");
                    declaredSize = parseInt(tokens[tokens.length - 1]);
                    for (int i = 0; i < tokens.length - 1; i++) {
                        exprs.add(tokens[i]);
                    }
                }
                if (declaredSize != null && declaredSize != exprs.size()) {
                    throw error("list new 的大小 " + declaredSize + " 与初始值数量 " + exprs.size() + " 不一致");
                }
                return new Stmt.ListNew(name, parseType(typeText, false), exprs,
                        declaredSize == null ? exprs.size() : declaredSize);
            }
            case "newv" -> {
                String name = cursor.word();
                String typeText = cursor.word();
                String expr = cursor.rest();
                requireName(name);
                requireExpr(expr, "list newv");
                return new Stmt.ListNewv(name, parseType(typeText, false), expr);
            }
            case "delete" -> {
                String name = cursor.word();
                requireName(name);
                return new Stmt.ListDelete(name);
            }
            case "resize" -> {
                String name = cursor.word();
                String expr = cursor.rest();
                requireName(name);
                requireExpr(expr, "list resize");
                return new Stmt.ListResize(name, expr);
            }
            default -> throw error("list 只支持 new / newv / delete / resize，实际是：" + sub);
        }
    }

    private Stmt parseIo(String line) {
        Cursor cursor = new Cursor(stripSemicolon(line.substring(3)));
        String sub = cursor.word();
        String tail = cursor.rest();
        switch (sub) {
            case "cout", "cerr" -> {
                int split = tail.lastIndexOf(" to ");
                if (split < 0) {
                    throw error("io " + sub + " 缺少 \" to 流\"");
                }
                String content = tail.substring(0, split).trim();
                StreamRef stream = StreamRef.parse(tail.substring(split + 4).trim());
                return "cout".equals(sub)
                        ? new Stmt.Cout(content, stream.name, stream.level)
                        : new Stmt.Cerr(content, stream.name, stream.level);
            }
            case "cin", "cinf" -> {
                return new Stmt.Unsupported("io " + sub + "（输入流在下一版开放）");
            }
            default -> throw error("io 只支持 cout / cerr / cin / cinf，实际是：" + sub);
        }
    }

    private Stmt parseLog(String line) {
        Cursor cursor = new Cursor(stripSemicolon(line.substring(4)));
        String sub = cursor.word();
        String tail = cursor.rest();
        switch (sub) {
            case "new", "open" -> {
                String keyword = "new".equals(sub) ? " to " : " from ";
                int split = tail.lastIndexOf(keyword);
                if (split < 0) {
                    throw error("log " + sub + " 缺少 \"" + keyword + "文件名\"");
                }
                String name = tail.substring(0, split).trim();
                String file = unquote(tail.substring(split + keyword.length()).trim());
                requireName(name);
                if (file.isBlank()) {
                    throw error("log " + sub + " 缺少文件名");
                }
                return "new".equals(sub) ? new Stmt.LogNew(name, file) : new Stmt.LogOpen(name, file);
            }
            case "close" -> {
                String name = tail.trim();
                requireName(name);
                return new Stmt.LogClose(name);
            }
            case "setl" -> {
                Cursor rest = new Cursor(tail);
                String name = rest.word();
                String level = rest.word();
                requireName(name);
                if (level.isBlank()) {
                    throw error("log setl 缺少等级");
                }
                return new Stmt.LogLevel(name, level);
            }
            default -> throw error("log 只支持 new / open / close / setl，实际是：" + sub);
        }
    }

    private Stmt parseFile(String line) {
        Cursor cursor = new Cursor(stripSemicolon(line.substring(5)));
        String sub = cursor.word();
        String tail = cursor.rest();
        switch (sub) {
            case "new", "open" -> {
                String keyword = "new".equals(sub) ? " to " : " from ";
                int split = tail.lastIndexOf(keyword);
                if (split < 0) {
                    throw error("file " + sub + " 缺少 \"" + keyword + "路径\"");
                }
                String name = tail.substring(0, split).trim();
                String path = unquote(tail.substring(split + keyword.length()).trim());
                requireName(name);
                if (path.isBlank()) {
                    throw error("file " + sub + " 缺少路径");
                }
                return "new".equals(sub) ? new Stmt.FileNew(name, path) : new Stmt.FileOpen(name, path);
            }
            case "close" -> {
                String name = tail.trim();
                requireName(name);
                return new Stmt.FileClose(name);
            }
            default -> throw error("file 只支持 new / open / close，实际是：" + sub);
        }
    }

    private Stmt parseSys(String line) {
        String rest = stripSemicolon(line.substring(4)).trim();
        if (rest.equals("exit")) {
            return new Stmt.SysExit();
        }
        if (rest.startsWith("module")) {
            return new Stmt.Unsupported("sys module（模块系统在下一版开放）");
        }
        throw error("sys 只支持 exit / module，实际是：" + rest);
    }

    // ---------------------------------------------------------------- 控制流

    private Stmt parseCtrl(String line) {
        String rest = line.substring(5).trim();
        if (rest.startsWith("if")) {
            return parseIf(rest);
        }
        if (rest.startsWith("for")) {
            return parseFor(rest);
        }
        if (rest.startsWith("while")) {
            return parseWhile(rest);
        }
        if (rest.startsWith("do")) {
            return parseDo(rest);
        }
        if (rest.startsWith("function")) {
            return parseFunction(rest);
        }
        if (rest.startsWith("func")) {
            return parseFuncCall(rest);
        }
        if (rest.startsWith("endfunc")) {
            String expr = stripSemicolon(rest.substring("endfunc".length()).trim());
            if (expr.startsWith("with")) {
                expr = expr.substring(4).trim();
                requireExpr(expr, "ctrl endfunc with");
                return new Stmt.Return(expr);
            }
            throw error("孤立的 ctrl endfunc;");
        }
        throw error("ctrl 只支持 if / for / while / do / function / func / endfunc，实际是：" + rest);
    }

    private Stmt parseIf(String header) {
        String condition = parenContent(header, "if");
        List<Stmt.Branch> branches = new ArrayList<>();
        branches.add(new Stmt.Branch(condition, parseBlock()));
        List<Stmt> elseBody = List.of();
        while (index < lines.length) {
            String line = lines[index].trim();
            if (line.startsWith("ctrl else if")) {
                currentLineNo = index + 1;
                String cond = parenContent(line, "else if");
                index++;
                branches.add(new Stmt.Branch(cond, parseBlock()));
                continue;
            }
            if (line.startsWith("ctrl else")) {
                index++;
                elseBody = parseBlock();
                continue;
            }
            if (line.startsWith("ctrl endif")) {
                index++;
                return new Stmt.If(branches, elseBody);
            }
            throw error("if 块缺少 ctrl endif;");
        }
        throw error("if 块未闭合，缺少 ctrl endif;");
    }

    private Stmt parseFor(String header) {
        String inside = parenContent(header, "for");
        List<String> parts = splitTopLevel(inside, ';');
        if (parts.size() != 3) {
            throw error("ctrl for(...) 需要三段：初始化; 条件; 迭代");
        }
        Stmt init = parts.get(0).isBlank() ? new Stmt.Nop() : parseLine(parts.get(0).trim());
        Stmt step = parts.get(2).isBlank() ? new Stmt.Nop() : parseLine(parts.get(2).trim());
        String condition = parts.get(1).trim();
        requireExpr(condition, "ctrl for 的条件");
        List<Stmt> body = parseBlock();
        expect("ctrl endfor", "ctrl endfor;");
        return new Stmt.For(init, condition, step, body);
    }

    private Stmt parseWhile(String header) {
        String condition = parenContent(header, "while");
        List<Stmt> body = parseBlock();
        expect("ctrl endwhile", "ctrl endwhile;");
        return new Stmt.While(condition, body);
    }

    private Stmt parseDo(String header) {
        List<Stmt> body = parseBlock();
        if (index >= lines.length || !lines[index].trim().startsWith("ctrl dwhile")) {
            throw error("ctrl do 块缺少 ctrl dwhile(...)");
        }
        String condition = parenContent(lines[index].trim(), "dwhile");
        index++;
        return new Stmt.DoWhile(body, condition);
    }

    private Stmt parseFunction(String header) {
        int open = header.indexOf('(');
        int close = matching(header, open, '(', ')');
        if (open < 0 || close < 0) {
            throw error("ctrl function 缺少形参括号");
        }
        String name = header.substring("function".length(), open).trim();
        requireName(name);
        List<String> params = new ArrayList<>();
        String inside = header.substring(open + 1, close).trim();
        if (!inside.isEmpty()) {
            for (String param : splitTopLevel(inside, ',')) {
                String trimmed = param.trim();
                requireName(trimmed);
                params.add(trimmed);
            }
        }
        List<Stmt> body = parseBlock();
        if (index < lines.length && isEndfunc(lines[index].trim())) {
            index++;
        }
        return new Stmt.FuncDecl(name, params, body);
    }

    private Stmt parseFuncCall(String header) {
        String rest = stripSemicolon(header.substring(4)).trim();
        return parseCallExpression(rest);
    }

    /** 把 {@code name(args)} 解析成函数调用。也用于 {@code var set x ctrl func f(1,2)} 这种右值。 */
    public static Stmt.FuncCall parseCallExpression(String text) {
        String rest = text.trim();
        int open = rest.indexOf('(');
        int close = open < 0 ? -1 : matching(rest, open, '(', ')');
        if (open < 0 || close < 0) {
            throw new CscSyntaxException("ctrl func 调用缺少实参括号：" + text);
        }
        String name = rest.substring(0, open).trim();
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new CscSyntaxException("非法的函数名：" + name);
        }
        List<String> args = new ArrayList<>();
        String inside = rest.substring(open + 1, close).trim();
        if (!inside.isEmpty()) {
            args.addAll(splitTopLevel(inside, ',').stream().map(String::trim).toList());
        }
        return new Stmt.FuncCall(name, args);
    }

    // ---------------------------------------------------------------- 工具

    private CscType parseType(String text, boolean required) {
        if ("auto".equalsIgnoreCase(text)) {
            return null;
        }
        return CscType.parse(text).orElseThrow(() ->
                new CscSyntaxException("未知的类型：" + text + "（第 " + currentLineNo + " 行）"));
    }

    private static String stripSemicolon(String text) {
        String trimmed = text.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1).replace("\\\"", "\"");
        }
        return text;
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new CscSyntaxException("需要整数，实际是：" + text);
        }
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw error("缺少名字");
        }
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw error("名字只能由字母、数字、下划线组成且不能以数字开头：" + name);
        }
    }

    private void requireExpr(String expr, String where) {
        if (expr == null || expr.isBlank()) {
            throw error(where + " 缺少表达式");
        }
    }

    private CscSyntaxException error(String message) {
        return new CscSyntaxException(message + "（第 " + currentLineNo + " 行）");
    }

    /** 取括号内容，例如 {@code if(%a% > 2):} 里的 {@code %a% > 2}。 */
    private String parenContent(String text, String what) {
        int open = text.indexOf('(');
        if (open < 0) {
            throw error("ctrl " + what + " 缺少括号");
        }
        int close = matching(text, open, '(', ')');
        if (close < 0) {
            throw error("ctrl " + what + " 括号未闭合");
        }
        String inside = text.substring(open + 1, close).trim();
        if (inside.isEmpty()) {
            throw error("ctrl " + what + " 的条件为空");
        }
        return inside;
    }

    /** 从 start 处的开括号找配对的闭括号，跳过字符串。 */
    private static int matching(String text, int start, char openCh, char closeCh) {
        if (start < 0 || start >= text.length()) {
            return -1;
        }
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                i++;
                while (i < text.length()) {
                    if (text.charAt(i) == '\\') {
                        i += 2;
                        continue;
                    }
                    if (text.charAt(i) == '"') {
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == openCh) {
                depth++;
            } else if (c == closeCh) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 按分隔符切分，忽略括号、方括号与字符串内部的分隔符。 */
    private static List<String> splitTopLevel(String text, char separator) {
        List<String> parts = new ArrayList<>();
        int depthParen = 0;
        int depthBracket = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                i++;
                while (i < text.length()) {
                    if (text.charAt(i) == '\\') {
                        i += 2;
                        continue;
                    }
                    if (text.charAt(i) == '"') {
                        break;
                    }
                    i++;
                }
                continue;
            }
            switch (c) {
                case '(' -> depthParen++;
                case ')' -> depthParen--;
                case '[' -> depthBracket++;
                case ']' -> depthBracket--;
                default -> {
                    if (c == separator && depthParen == 0 && depthBracket == 0) {
                        parts.add(text.substring(start, i));
                        start = i + 1;
                    }
                }
            }
        }
        parts.add(text.substring(start));
        List<String> cleaned = new ArrayList<>(parts.size());
        for (String part : parts) {
            cleaned.add(part.trim());
        }
        return cleaned;
    }

    /** 去掉行注释与块注释，保留换行以便行号不变。字符串内部不处理。 */
    static String stripComments(String script) {
        StringBuilder sb = new StringBuilder(script.length());
        int i = 0;
        boolean inString = false;
        while (i < script.length()) {
            char c = script.charAt(i);
            if (inString) {
                sb.append(c);
                if (c == '\\' && i + 1 < script.length()) {
                    sb.append(script.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                sb.append(c);
                i++;
                continue;
            }
            if (c == '/' && i + 1 < script.length()) {
                char next = script.charAt(i + 1);
                if (next == '/') {
                    i += 2;
                    while (i < script.length() && script.charAt(i) != '\n') {
                        i++;
                    }
                    continue;
                }
                if (next == '*') {
                    i += 2;
                    while (i + 1 < script.length()
                            && !(script.charAt(i) == '*' && script.charAt(i + 1) == '/')) {
                        if (script.charAt(i) == '\n') {
                            sb.append('\n');
                        }
                        i++;
                    }
                    i = Math.min(script.length(), i + 2);
                    sb.append(' ');
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** 流名 + 单条等级，例如 {@code log1[ERR]}。 */
    private record StreamRef(String name, String level) {

        static StreamRef parse(String text) {
            String trimmed = text.trim();
            int open = trimmed.indexOf('[');
            if (open > 0 && trimmed.endsWith("]")) {
                return new StreamRef(trimmed.substring(0, open).trim(),
                        trimmed.substring(open + 1, trimmed.length() - 1).trim());
            }
            return new StreamRef(trimmed, null);
        }
    }

    /** 极简游标：按空白取词，剩下的当表达式。 */
    private static final class Cursor {

        private final String text;
        private int pos;

        Cursor(String text) {
            this.text = text == null ? "" : text;
        }

        String word() {
            skip();
            int start = pos;
            while (pos < text.length() && !Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
            return text.substring(start, pos);
        }

        String rest() {
            skip();
            String remaining = text.substring(pos);
            pos = text.length();
            return remaining.trim();
        }

        private void skip() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }
    }
}
