import currkill.sc_csc.api.CscContext;
import currkill.sc_csc.api.CscDiagnostic;
import currkill.sc_csc.api.CscEvaluator;
import currkill.sc_csc.api.CscSeverity;
import currkill.sc_csc.api.CscType;
import currkill.sc_csc.api.CscValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CSC 冒烟测试。纯 JDK，不依赖测试框架，{@code ./gradlew smokeTest} 跑。
 */
public final class SmokeTest {

    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        FakeContext ctx = new FakeContext();
        ctx.declare("a", CscType.INT, CscValue.ofInt(5));
        ctx.declare("s", CscType.STRING, CscValue.ofString("hi"));
        ctx.declare("flag", CscType.BOOL, CscValue.ofBool(true));
        ctx.declare("a*(b+c)", CscType.INT, CscValue.ofInt(9));

        // ---------- 转义
        expect("反转义", fmt("a\\\\b", ctx), "a\\b");
        expect("换行", fmt("a\\nb", ctx), "a\nb");
        expect("制表", fmt("a\\tb", ctx), "a\tb");
        expect("字面百分号", fmt("100\\%", ctx), "100%");
        expect("字面感叹号", fmt("ok \\! fine", ctx), "ok ! fine");
        expect("未知转义按字面", fmt("a\\xb", ctx), "a\\xb");
        expect("结尾反斜杠", fmt("ab\\", ctx), "ab\\");

        // ---------- 注释
        expect("行注释", fmt("a // 注释\nb", ctx), "a \nb");
        expect("块注释", fmt("a /* 注释 */ b", ctx), "a  b");
        expect("块注释跨行", fmt("a /* x\ny */ b", ctx), "a  b");
        expect("未闭合块注释报错", Boolean.toString(
                fmtDiagnostic("a /* x", ctx).message().contains("未闭合的块注释")), "true");

        // ---------- 变量
        expect("变量展开", fmt("%a%", ctx), "5");
        expect("变量名两边空格被 trim", fmt("% a %", ctx), "5");
        expect("名字含运算符的变量", fmt("%a*(b+c)%", ctx), "9");
        expect("未闭合百分号按字面", fmt("%abc", ctx), "%abc");
        expect("未知变量原样保留", fmt("%nope%", ctx), "%nope%");

        // ---------- 表达式
        expect("加法", fmt("\\!(1+2)", ctx), "3");
        expect("整数除法", fmt("\\!(7/2)", ctx), "3");
        expect("浮点除法", fmt("\\!(7.0/2)", ctx), "3.5");
        expect("取模", fmt("\\!(7 %mod 3)", ctx), "1");
        expect("幂", fmt("\\!(2**10)", ctx), "1024");
        expect("幂右结合", fmt("\\!(2**3**2)", ctx), "512");
        expect("一元负号与幂", fmt("\\!(-2**2)", ctx), "-4");
        expect("比较", fmt("\\!(3 > 2)", ctx), "true");
        expect("相等", fmt("\\!(\"x\" == \"x\")", ctx), "true");
        expect("逻辑与", fmt("\\!(true && %flag%)", ctx), "true");
        expect("逻辑或", fmt("\\!(false || true)", ctx), "true");
        expect("按位异或", fmt("\\!(5 ^ 3)", ctx), "6");
        expect("字符串拼接", fmt("\\!(\"v=\" + %a%)", ctx), "v=5");
        expect("变量参与运算", fmt("\\!(%a% * 2)", ctx), "10");
        expect("后缀拼接", fmt("\\!(1+2)A+B", ctx), "3A+B");
        expect("嵌套括号", fmt("\\!((1+2)*(3+4))", ctx), "21");
        expect("除零原样保留", fmt("\\!(1/0)", ctx), "\\!(1/0)");

        // ---------- 脚本：变量
        run("var new n int 1", ctx);
        expect("var new", fmt("%n%", ctx), "1");
        run("var set n %n% + 41", ctx);
        expect("var set 右值是表达式", fmt("%n%", ctx), "42");
        run("var delete n", ctx);
        expect("var delete", Boolean.toString(ctx.hasVariable("n")), "false");
        run("var new f float 1.9", ctx);
        expect("var new float", fmt("%f%", ctx), "1.9");
        run("var new z auto 7", ctx);
        expect("auto 推断类型", fmt("%z%", ctx), "7");
        run("var new total int 0", ctx);

        // ---------- 脚本：列表
        run("list new arr int [10, 20, 30]", ctx);
        expect("列表长度", fmt("\\!(%arr%[size])", ctx), "3");
        expect("取下标", fmt("\\!(%arr%[1])", ctx), "20");
        run("list resize arr 2", ctx);
        expect("resize 之后长度", fmt("\\!(%arr%[size])", ctx), "2");
        run("list newv one string \"x\"", ctx);
        expect("newv 单元素", fmt("\\!(%one%[size])", ctx), "1");

        // ---------- 脚本：控制流
        run("""
                ctrl for(var new i int 0; %i% < 4; var set i %i% + 1):
                  var set total %total% + %i%
                ctrl endfor
                """, ctx);
        expect("for 累加", fmt("%total%", ctx), "6");

        run("""
                var new k int 0
                ctrl while(%k% < 3):
                  var set k %k% + 1
                ctrl endwhile
                """, ctx);
        expect("while", fmt("%k%", ctx), "3");

        run("""
                var new m int 5
                ctrl if(%m% > 10):
                  var set m 100
                ctrl else if(%m% > 3):
                  var set m 50
                ctrl else:
                  var set m 0
                ctrl endif
                """, ctx);
        expect("if/else if/else", fmt("%m%", ctx), "50");

        run("""
                ctrl function twice(x):
                  ctrl endfunc with %x% * 2
                ctrl endfunc
                var new r int 0
                var set r ctrl func twice(21)
                """, ctx);
        expect("函数与返回值", fmt("%r%", ctx), "42");

        // ---------- 脚本：输出与流
        run("io cout \"a is %a%\" to text", ctx);
        expect("io cout 到 text", ctx.last(ctx.output), "a is 5");
        run("io cerr \"bad\" to text", ctx);
        expect("io cerr 到 text", ctx.last(ctx.errors), "bad");

        run("log new log1 to test.log", ctx);
        run("io cout \"line %a%\" to log1", ctx);
        expect("日志流写入", Boolean.toString(ctx.streamWrites.contains("log1:line 5")), "true");

        run("file new f1 to notes.txt", ctx);
        run("io cout \"hello\" to f1", ctx);
        expect("文件流写入", Boolean.toString(ctx.streamWrites.contains("f1:hello")), "true");

        // ---------- sys exit
        ctx.output.clear();
        run("""
                io cout "before" to text
                sys exit
                io cout "after" to text
                """, ctx);
        expect("sys exit 截断脚本", Integer.toString(ctx.output.size()), "1");
        expect("sys exit 通知了宿主", Boolean.toString(ctx.exited), "true");

        // ---------- 诊断
        FakeContext conv = new FakeContext();
        conv.declare("i", CscType.INT, CscValue.ofInt(1));
        fmt("\\!(%i% + 1.5)", conv);
        expect("int + float 记隐式转换", Boolean.toString(conv.conversionCount() > 0), "true");

        FakeContext unknownVar = new FakeContext();
        fmt("%nope%", unknownVar);
        expect("未知变量报严重错误", Boolean.toString(unknownVar.hasSevere("未知变量")), "true");

        FakeContext badCall = new FakeContext();
        run("ctrl func nope(1)", badCall);
        expect("未定义函数报严重错误", Boolean.toString(badCall.hasSevere("未定义的函数")), "true");

        FakeContext module = new FakeContext();
        run("sys module load m from csc \"x\"", module);
        expect("模块系统报未实现", Boolean.toString(module.hasSevere("模块系统")), "true");

        FakeContext badCast = new FakeContext();
        badCast.declare("t", CscType.INT, CscValue.ofInt(1));
        run("var set t \"abc\"", badCast);
        expect("string -> int 失败报严重错误", Boolean.toString(badCast.hasSevere("无法把")), "true");

        FakeContext redeclare = new FakeContext();
        redeclare.declare("d", CscType.INT, CscValue.ofInt(1));
        run("var new d int 2", redeclare);
        expect("重复声明报错", Boolean.toString(redeclare.hasSevere("已存在")), "true");

        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static String fmt(String text, FakeContext ctx) {
        ctx.diagnostics.clear();
        return CscEvaluator.formatText(text, ctx);
    }

    private static CscDiagnostic fmtDiagnostic(String text, FakeContext ctx) {
        ctx.diagnostics.clear();
        CscEvaluator.formatText(text, ctx);
        return ctx.diagnostics.isEmpty()
                ? new CscDiagnostic(CscSeverity.SEVERE, "<无诊断>", -1)
                : ctx.diagnostics.get(0);
    }

    private static void run(String script, FakeContext ctx) {
        CscEvaluator.evaluateScript(script, ctx);
    }

    private static void expect(String label, String actual, String expected) {
        if (expected.equals(actual)) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + label + " -> got [" + escape(actual) + "] want [" + escape(expected) + "]");
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r");
    }

    /** 内存版上下文，不依赖 Minecraft。 */
    static final class FakeContext implements CscContext {

        final Map<String, CscValue> values = new LinkedHashMap<>();
        final Map<String, CscType> types = new LinkedHashMap<>();
        final List<String> output = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<String> streamWrites = new ArrayList<>();
        final List<CscDiagnostic> diagnostics = new ArrayList<>();
        final Set<String> streams = new LinkedHashSet<>();
        boolean exited;

        void declare(String name, CscType type, CscValue value) {
            values.put(name, value);
            types.put(name, type);
        }

        String last(List<String> list) {
            return list.isEmpty() ? "" : list.get(list.size() - 1);
        }

        public boolean hasVariable(String name) {
            return values.containsKey(name);
        }

        public CscValue getVariable(String name) {
            return values.get(name);
        }

        public CscType declaredType(String name) {
            return types.get(name);
        }

        public void declareVariable(String name, CscType type, CscValue initial) {
            values.put(name, initial);
            types.put(name, type);
        }

        public void setVariable(String name, CscValue value) {
            values.put(name, value);
        }

        public void deleteVariable(String name) {
            values.remove(name);
            types.remove(name);
        }

        public Set<String> variableNames() {
            return Set.copyOf(values.keySet());
        }

        public void cout(String text) {
            output.add(text);
        }

        public void cerr(String text) {
            errors.add(text);
        }

        public boolean hasStream(String name) {
            return streams.contains(name);
        }

        public boolean openLog(String name, String fileName) {
            streams.add(name);
            return true;
        }

        public boolean openFile(String name, String path) {
            streams.add(name);
            return true;
        }

        public boolean closeStream(String name) {
            return streams.remove(name);
        }

        public boolean setStreamLevel(String name, String level) {
            return streams.contains(name);
        }

        public void writeStream(String name, String text, String level) {
            if (!streams.contains(name)) {
                report(CscDiagnostic.severe("流不存在：" + name, -1));
                return;
            }
            streamWrites.add(name + ":" + text);
        }

        public void report(CscDiagnostic diagnostic) {
            diagnostics.add(diagnostic);
        }

        public void exitScript() {
            exited = true;
        }

        boolean hasSevere(String fragment) {
            return diagnostics.stream().anyMatch(d -> d.isSevere() && d.message().contains(fragment));
        }

        long conversionCount() {
            return diagnostics.stream().filter(d -> d.severity() == CscSeverity.CONVERSION).count();
        }
    }
}
