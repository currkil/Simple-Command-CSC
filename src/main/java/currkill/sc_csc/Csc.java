package currkill.sc_csc;

import currkill.sc_csc.api.Diagnostic;
import currkill.sc_csc.api.EvaluationException;
import currkill.sc_csc.api.FormatResult;
import currkill.sc_csc.api.VariableProvider;
import currkill.sc_csc.internal.ExpressionParser;
import currkill.sc_csc.internal.TextFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * CSC（Currkill's Simple Command Language）对外的唯一入口。
 *
 * <p>纯 JDK、无 Minecraft 依赖、无 IO：只把字符串变成一个结果，输出和日志由调用方负责。
 * 子模组不直接依赖本类，而是走 SC Core 的 {@code SimpleCommandCore.formatText(...)} 封装。
 */
public final class Csc {

    public static final String VERSION = "1.0.0";

    private Csc() {
    }

    /**
     * 格式化一段文本：处理转义、注释、变量、表达式。
     *
     * @param raw  原始文本，可为 null
     * @param vars 变量来源，可为 null（等价于空来源）
     */
    public static FormatResult format(String raw, VariableProvider vars) {
        return TextFormatter.format(raw, vars);
    }

    /** 只要结果文本，丢掉诊断。需要提示玩家时请用 {@link #format}。 */
    public static String formatText(String raw, VariableProvider vars) {
        return format(raw, vars).text();
    }

    /**
     * 直接求值一条裸表达式（不带 {@code /!( )} 包装），给脚本层/终端复用。
     *
     * @throws EvaluationException 表达式有语法错误、除零、未知变量等
     */
    public static String evaluate(String expression, VariableProvider vars) {
        if (expression == null || expression.isBlank()) {
            throw new EvaluationException("表达式为空");
        }
        VariableProvider source = vars == null ? VariableProvider.empty() : vars;
        List<Diagnostic> sink = new ArrayList<>();
        return new ExpressionParser(expression, source, sink, 0).parse().asText();
    }
}
