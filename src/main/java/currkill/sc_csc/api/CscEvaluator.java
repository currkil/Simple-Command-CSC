package currkill.sc_csc.api;

import currkill.sc_csc.internal.Engine;

/**
 * CSC 的求值入口。
 *
 * <p>三条入口各司其职：{@link #evaluate} 求值一条裸表达式，{@link #evaluateScript}
 * 执行脚本，{@link #formatText} 把带变量与表达式的文本展开成最终字符串。
 *
 * <p>所有结果都是 {@link CscValue}，不再以裸字符串在接口间传递。诊断通过
 * {@link CscContext#report} 交回宿主，CSC 自己不打印、不写文件。
 */
public final class CscEvaluator {

    private CscEvaluator() {
    }

    /**
     * 求值一条裸表达式，例如 {@code %a% * 2}。
     *
     * @throws CscSyntaxException     语法错误
     * @throws CscConversionException 类型转换失败
     * @throws CscRuntimeException    除零、未知变量等
     */
    public static CscValue evaluate(String expression, CscContext context) {
        return Engine.evaluate(expression, context);
    }

    /**
     * 执行一段脚本（语句序列）。
     *
     * @return 脚本最后一条表达式的值；没有则 {@link CscValue#ofVoid()}
     */
    public static CscValue evaluateScript(String script, CscContext context) {
        return Engine.evaluateScript(script, context);
    }

    /**
     * 文本模式：处理转义、注释、变量、表达式，返回展开后的字符串。
     *
     * <p>出错片段原样保留，其余片段正常输出；错误通过 {@code context.report} 交回宿主。
     */
    public static String formatText(String text, CscContext context) {
        return Engine.formatText(text, context);
    }
}
