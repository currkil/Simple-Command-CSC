package currkill.sc_csc.internal;

import currkill.sc_csc.api.CscContext;
import currkill.sc_csc.api.CscRuntimeException;
import currkill.sc_csc.api.CscType;
import currkill.sc_csc.api.CscValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本解释器：逐条执行语句。
 *
 * <p>控制流用两个内部信号实现：{@link ReturnSignal} 表示函数返回，{@link ExitSignal}
 * 表示 {@code sys exit}。循环有临时迭代上限（完整沙箱在下一版开放）。
 */
public final class Interpreter {

    /** 临时安全阀：完整沙箱下一版开放，这里先防止死循环卡住服务器。 */
    private static final long MAX_ITERATIONS = 200_000L;
    private static final int MAX_CALL_DEPTH = 100;

    private final ScopedContext scope;
    private final Map<String, Stmt.FuncDecl> functions = new HashMap<>();
    private long iterations;
    private int callDepth;

    public Interpreter(CscContext context) {
        this.scope = new ScopedContext(context);
    }

    public void run(List<Stmt> program) {
        try {
            execBlock(program);
        } catch (ExitSignal ignored) {
            // sys exit：正常结束，不算错误
        }
    }

    /** 执行一个语句块；不吞 sys exit 信号，保证它一路向上冒到 {@link #run}。 */
    private void execBlock(List<Stmt> block) {
        for (Stmt statement : block) {
            execute(statement);
        }
    }

    // ---------------------------------------------------------------- 语句

    private void execute(Stmt statement) {
        if (statement instanceof Stmt.Nop) {
            return;
        }
        if (statement instanceof Stmt.VarNew s) {
            varNew(s);
            return;
        }
        if (statement instanceof Stmt.VarSet s) {
            varSet(s);
            return;
        }
        if (statement instanceof Stmt.VarDelete s) {
            if (!scope.hasVariable(s.name())) {
                throw new CscRuntimeException("变量不存在：%" + s.name() + "%");
            }
            scope.deleteVariable(s.name());
            return;
        }
        if (statement instanceof Stmt.ListNew s) {
            listNew(s);
            return;
        }
        if (statement instanceof Stmt.ListNewv s) {
            listNewv(s);
            return;
        }
        if (statement instanceof Stmt.ListDelete s) {
            if (!scope.hasVariable(s.name())) {
                throw new CscRuntimeException("列表不存在：%" + s.name() + "%");
            }
            scope.deleteVariable(s.name());
            return;
        }
        if (statement instanceof Stmt.ListResize s) {
            listResize(s);
            return;
        }
        if (statement instanceof Stmt.If s) {
            for (Stmt.Branch branch : s.branches()) {
                if (asBool(evaluate(branch.condition()))) {
                    execBlock(branch.body());
                    return;
                }
            }
            execBlock(s.elseBody());
            return;
        }
        if (statement instanceof Stmt.For s) {
            execute(s.init());
            while (asBool(evaluate(s.condition()))) {
                tick();
                execBlock(s.body());
                execute(s.step());
            }
            return;
        }
        if (statement instanceof Stmt.While s) {
            while (asBool(evaluate(s.condition()))) {
                tick();
                execBlock(s.body());
            }
            return;
        }
        if (statement instanceof Stmt.DoWhile s) {
            do {
                tick();
                execBlock(s.body());
            } while (asBool(evaluate(s.condition())));
            return;
        }
        if (statement instanceof Stmt.FuncDecl s) {
            functions.put(s.name(), s);
            return;
        }
        if (statement instanceof Stmt.FuncCall s) {
            invoke(s.name(), s.argExprs());
            return;
        }
        if (statement instanceof Stmt.Return s) {
            throw new ReturnSignal(evaluate(s.expr()));
        }
        if (statement instanceof Stmt.Cout s) {
            emit(s.content(), s.stream(), s.level(), false);
            return;
        }
        if (statement instanceof Stmt.Cerr s) {
            emit(s.content(), s.stream(), s.level(), true);
            return;
        }
        if (statement instanceof Stmt.LogNew s) {
            if (!scope.openLog(s.name(), s.fileName())) {
                throw new CscRuntimeException("无法新建日志流 " + s.name() + " -> " + s.fileName());
            }
            return;
        }
        if (statement instanceof Stmt.LogOpen s) {
            if (!scope.openLog(s.name(), s.fileName())) {
                throw new CscRuntimeException("无法打开日志流 " + s.name() + " -> " + s.fileName());
            }
            return;
        }
        if (statement instanceof Stmt.LogLevel s) {
            if (!scope.setStreamLevel(s.name(), s.level())) {
                throw new CscRuntimeException("无法设置日志流等级：" + s.name());
            }
            return;
        }
        if (statement instanceof Stmt.LogClose s) {
            scope.closeStream(s.name());
            return;
        }
        if (statement instanceof Stmt.FileNew s) {
            if (!scope.openFile(s.name(), s.path())) {
                throw new CscRuntimeException("无法新建文件流 " + s.name() + " -> " + s.path());
            }
            return;
        }
        if (statement instanceof Stmt.FileOpen s) {
            if (!scope.openFile(s.name(), s.path())) {
                throw new CscRuntimeException("无法打开文件流 " + s.name() + " -> " + s.path());
            }
            return;
        }
        if (statement instanceof Stmt.FileClose s) {
            scope.closeStream(s.name());
            return;
        }
        if (statement instanceof Stmt.SysExit) {
            // 先通知宿主（REPL 据此退出会话、关闭流），再中断执行
            scope.exitScript();
            throw new ExitSignal();
        }
        if (statement instanceof Stmt.Unsupported s) {
            throw new CscRuntimeException(s.feature());
        }
        throw new CscRuntimeException("未知语句：" + statement.getClass().getSimpleName());
    }

    // ---------------------------------------------------------------- 变量与列表

    private void varNew(Stmt.VarNew statement) {
        if (scope.hasVariable(statement.name())) {
            throw new CscRuntimeException("变量已存在：%" + statement.name() + "%（用 var set 修改）");
        }
        CscValue value = evaluateValue(statement.expr());
        CscType declared = statement.type() == null ? value.type() : statement.type();
        if (declared == CscType.VOID) {
            throw new CscRuntimeException("变量不能声明为 void");
        }
        CscValue converted = declared == value.type()
                ? value
                : Conversions.to(declared, value, scope, 0, "var new");
        scope.declareVariable(statement.name(), declared, converted);
    }

    private void varSet(Stmt.VarSet statement) {
        if (!scope.hasVariable(statement.name())) {
            throw new CscRuntimeException("变量不存在：%" + statement.name() + "%（用 var new 声明）");
        }
        CscType declared = scope.declaredType(statement.name());
        CscValue value = evaluateValue(statement.expr());
        if (declared != null && declared != value.type()) {
            value = Conversions.to(declared, value, scope, 0, "var set");
        }
        scope.setVariable(statement.name(), value);
    }

    private void listNew(Stmt.ListNew statement) {
        if (scope.hasVariable(statement.name())) {
            throw new CscRuntimeException("列表已存在：%" + statement.name() + "%");
        }
        List<CscValue> values = new ArrayList<>();
        CscType elementType = statement.elementType();
        for (String expr : statement.valueExprs()) {
            CscValue value = evaluate(expr);
            if (elementType != null && elementType != CscType.VOID) {
                value = elementType == value.type()
                        ? value
                        : Conversions.to(elementType, value, scope, 0, "list new");
            } else if (elementType == null) {
                elementType = value.type();
            }
            values.add(value);
        }
        if (statement.declaredSize() != null && statement.declaredSize() != values.size()) {
            throw new CscRuntimeException("list new 的大小 " + statement.declaredSize()
                    + " 与初始值数量 " + values.size() + " 不一致");
        }
        scope.declareVariable(statement.name(), CscType.LIST, CscValue.ofList(values));
    }

    private void listNewv(Stmt.ListNewv statement) {
        if (scope.hasVariable(statement.name())) {
            throw new CscRuntimeException("列表已存在：%" + statement.name() + "%");
        }
        CscValue value = evaluateValue(statement.expr());
        if (statement.elementType() != null && statement.elementType() != value.type()) {
            value = Conversions.to(statement.elementType(), value, scope, 0, "list newv");
        }
        scope.declareVariable(statement.name(), CscType.LIST, CscValue.ofList(List.of(value)));
    }

    private void listResize(Stmt.ListResize statement) {
        if (!scope.hasVariable(statement.name())) {
            throw new CscRuntimeException("列表不存在：%" + statement.name() + "%");
        }
        long size = Conversions.asInt(evaluate(statement.expr()), scope, 0, "list resize");
        if (size < 0) {
            throw new CscRuntimeException("列表大小不能为负：" + size);
        }
        List<CscValue> current = new ArrayList<>(scope.getVariable(statement.name()).asList());
        while (current.size() > size) {
            current.remove(current.size() - 1);
        }
        CscValue filler = current.isEmpty() ? CscValue.ofInt(0) : current.get(0);
        while (current.size() < size) {
            current.add(filler);
        }
        scope.setVariable(statement.name(), CscValue.ofList(current));
    }

    // ---------------------------------------------------------------- 输出

    private void emit(String content, String stream, String level, boolean isError) {
        String text = formatContent(content);
        if ("text".equals(stream)) {
            if (isError) {
                scope.cerr(text);
            } else {
                scope.cout(text);
            }
            return;
        }
        scope.writeStream(stream, text, level != null ? level : (isError ? "ERR" : null));
    }

    private String formatContent(String content) {
        String text = content;
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).replace("\\\"", "\"");
        }
        return TextFormatter.format(text, scope);
    }

    // ---------------------------------------------------------------- 函数

    private CscValue invoke(String name, List<String> argExprs) {
        Stmt.FuncDecl function = functions.get(name);
        if (function == null) {
            throw new CscRuntimeException("未定义的函数：" + name);
        }
        List<CscValue> args = new ArrayList<>();
        for (String expr : argExprs) {
            args.add(evaluate(expr));
        }
        if (args.size() != function.params().size()) {
            throw new CscRuntimeException("函数 " + name + " 需要 " + function.params().size()
                    + " 个参数，实际给了 " + args.size() + " 个");
        }
        if (callDepth >= MAX_CALL_DEPTH) {
            throw new CscRuntimeException("函数递归超过 " + MAX_CALL_DEPTH + " 层");
        }
        Map<String, CscValue> frame = ScopedContext.newFrame();
        Map<String, CscType> types = ScopedContext.newTypeFrame();
        for (int i = 0; i < args.size(); i++) {
            frame.put(function.params().get(i), args.get(i));
            types.put(function.params().get(i), args.get(i).type());
        }
        scope.push(frame, types);
        callDepth++;
        try {
            for (Stmt statement : function.body()) {
                execute(statement);
            }
            return CscValue.ofVoid();
        } catch (ReturnSignal signal) {
            return signal.value;
        } finally {
            callDepth--;
            scope.pop();
        }
    }

    // ---------------------------------------------------------------- 工具

    private CscValue evaluate(String expression) {
        return ExpressionParser.parseFull(expression, scope, 0);
    }

    /** 值位置：普通表达式，或 {@code ctrl func f(...)} 形式的函数调用。 */
    private CscValue evaluateValue(String expression) {
        String text = expression == null ? "" : expression.trim();
        if (text.startsWith("ctrl")) {
            String rest = text.substring(4).trim();
            if (rest.startsWith("func")) {
                Stmt.FuncCall call = ScriptParser.parseCallExpression(rest.substring(4).trim());
                return invoke(call.name(), call.argExprs());
            }
        }
        return evaluate(expression);
    }

    private boolean asBool(CscValue value) {
        return Conversions.asBool(value, scope, 0, "条件");
    }

    private void tick() {
        if (++iterations > MAX_ITERATIONS) {
            throw new CscRuntimeException("循环迭代超过上限 " + MAX_ITERATIONS + "（完整沙箱在下一版开放）");
        }
    }

    /** 函数返回信号。 */
    private static final class ReturnSignal extends RuntimeException {
        private final CscValue value;

        ReturnSignal(CscValue value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    /** {@code sys exit} 信号。 */
    private static final class ExitSignal extends RuntimeException {
        ExitSignal() {
            super(null, null, false, false);
        }
    }
}
