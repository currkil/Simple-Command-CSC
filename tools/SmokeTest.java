import currkill.sc_csc.Csc;
import currkill.sc_csc.api.Diagnostic;
import currkill.sc_csc.api.FormatResult;
import currkill.sc_csc.api.VariableProvider;

import java.util.Map;

/**
 * CSC 语言层冒烟测试。纯 JDK，不依赖测试框架，直接 {@code ./gradlew smokeTest} 跑。
 */
public final class SmokeTest {

    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        VariableProvider vars = VariableProvider.ofMap(Map.of("name", "Steve", "n", "5", "flag", "true"));

        // 转义
        check("a//b", "a/b", vars);
        check("a/nb", "a\nb", vars);
        check("a/tb", "a\tb", vars);
        check("a/rb", "a\rb", vars);
        check("100/%", "100%", vars);
        check("say /! hi", "say ! hi", vars);
        check("a/\\b", "a\\b", vars);

        // 注释
        check("hi\\ignore me\\there", "hithere", vars);
        check("a\\\\b", "ab", vars);

        // 变量
        check("hello %name%!", "hello Steve!", vars);
        check("value = %n%", "value = 5", vars);

        // 表达式
        check("sum /!(1+2)", "sum 3", vars);
        check("sum /!(1+2)*3", "sum 3*3", vars);
        check("/!(2**3**2)", "512", vars);
        check("/!(7/@2)", "1", vars);
        check("/!(2**-1)", "0.5", vars);
        check("/!(-2**2)", "-4", vars);
        check("/!('a'+1)", "a1", vars);
        check("/!(1.5*2)", "3", vars);
        check("/!(1/3)", "0.3333333333333333", vars);
        check("/!(true && %flag%)", "true", vars);
        check("/!(1 || 0)", "true", vars);
        check("/!(1 ^ 0)", "true", vars);
        check("/!(%n% * 2)", "10", vars);
        check("/!(%n% + 1)", "51", vars);
        check("/!((1+2)*3)", "9", vars);
        check("/!('a' + 'b')", "ab", vars);
        check("x/!(1+1)y", "x2y", vars);

        // 容错：出错片段原样保留
        FormatResult divide = Csc.format("/!(1/0)", vars);
        expect("除零保留原样", divide.text(), "/!(1/0)");
        expect("除零是严重错误", String.valueOf(divide.hasSevere()), "true");

        FormatResult unknown = Csc.format("%nope%", vars);
        expect("未知变量保留原样", unknown.text(), "%nope%");
        expect("未知变量是严重错误", String.valueOf(unknown.hasSevere()), "true");

        FormatResult unclosedComment = Csc.format("a\\b", vars);
        expect("未闭合注释保留原样", unclosedComment.text(), "a\\b");
        expect("未闭合注释是严重错误", String.valueOf(unclosedComment.hasSevere()), "true");

        FormatResult unclosedExpr = Csc.format("/!(1+2", vars);
        expect("未闭合表达式保留原样", unclosedExpr.text(), "/!(1+2");
        expect("未闭合表达式是严重错误", String.valueOf(unclosedExpr.hasSevere()), "true");

        FormatResult unclosedVar = Csc.format("%abc", vars);
        expect("未闭合变量保留原样", unclosedVar.text(), "%abc");

        FormatResult badEscape = Csc.format("a/xb", vars);
        expect("未知转义保留原样", badEscape.text(), "a/xb");
        expect("未知转义是严重错误", String.valueOf(badEscape.hasSevere()), "true");

        // 隐式转换只记 CONVERSION
        FormatResult conversion = Csc.format("/!(1 + '2')", vars);
        expect("有隐式转换记录", String.valueOf(conversion.conversions().size() >= 1), "true");
        expect("隐式转换不是严重错误", String.valueOf(conversion.hasSevere()), "false");

        // 多片段：一段错不影响另一段
        FormatResult mixed = Csc.format("ok:%missing%:/!(1/0):end", vars);
        expect("多片段其他部分正常", mixed.text(), "ok:%missing%:/!(1/0):end");

        for (Diagnostic diagnostic : unclosedComment.diagnostics()) {
            System.out.println("  diag: " + diagnostic);
        }

        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static void check(String input, String expected, VariableProvider vars) {
        expect("format(" + input + ")", Csc.format(input, vars).text(), expected);
    }

    private static void expect(String label, String actual, String expected) {
        if (expected.equals(actual)) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + label + " -> got ["
                    + actual.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                    + "] want [" + expected.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "]");
        }
    }
}
