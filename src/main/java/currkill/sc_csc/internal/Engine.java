package currkill.sc_csc.internal;

import currkill.sc_csc.api.CscContext;
import currkill.sc_csc.api.CscDiagnostic;
import currkill.sc_csc.api.CscRuntimeException;
import currkill.sc_csc.api.CscValue;

import java.util.List;

/**
 * 内部总入口：把三个面部入口接到具体实现上。
 */
public final class Engine {

    private Engine() {
    }

    /** 求值一条裸表达式；异常向调用方抛出。 */
    public static CscValue evaluate(String expression, CscContext context) {
        if (expression == null || expression.isBlank()) {
            throw new CscRuntimeException("表达式为空");
        }
        return ExpressionParser.parseFull(expression, context, 0);
    }

    /** 解析脚本，语法错误以异常抛出。 */
    public static List<Stmt> parseScript(String script) {
        return ScriptParser.parse(script);
    }

    /**
     * 执行脚本。语法错误与运行时错误都会通过 {@code context.report} 记成严重错误并终止执行，
     * 不会向调用方抛异常——这一层是给指令用的。
     */
    public static CscValue evaluateScript(String script, CscContext context) {
        List<Stmt> program;
        try {
            program = ScriptParser.parse(script);
        } catch (CscRuntimeException e) {
            context.report(CscDiagnostic.severe(e.getMessage(), -1));
            return CscValue.ofVoid();
        }
        try {
            new Interpreter(context).run(program);
        } catch (CscRuntimeException e) {
            context.report(CscDiagnostic.severe(e.getMessage(), -1));
        }
        return CscValue.ofVoid();
    }

    /** 文本模式格式化。 */
    public static String formatText(String text, CscContext context) {
        return TextFormatter.format(text, context);
    }
}
