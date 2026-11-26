package currkill.sc_csc.api;

import java.util.List;
import java.util.Set;

/**
 * 宿主上下文。CSC 本体不认识 Minecraft，所有对外交互都走这个接口。
 *
 * <p>职责划分：CSC 负责解析与求值，**往哪里写、写到什么等级、文件落在哪个目录**
 * 全由宿主决定。宿主据此实现沙箱与权限。
 *
 * <p>实现方至少要保证：变量的声明/赋值/删除是持久的（同一上下文内），
 * {@link #report} 会被调用到，{@link #exitScript} 能中断当前脚本。
 */
public interface CscContext {

    // ---------------------------------------------------------------- 变量

    boolean hasVariable(String name);

    /** 不存在时返回 {@code null}。 */
    CscValue getVariable(String name);

    /** 变量的声明类型；未声明返回 {@code null}。 */
    CscType declaredType(String name);

    /** 声明一个新变量。宿主应当拒绝重复声明（返回错误由解释器报告）。 */
    void declareVariable(String name, CscType type, CscValue initial);

    void setVariable(String name, CscValue value);

    void deleteVariable(String name);

    Set<String> variableNames();

    // ---------------------------------------------------------------- 输出

    /** 标准输出（聊天栏/控制台）。 */
    void cout(String text);

    /** 标准错误输出。 */
    void cerr(String text);

    // ---------------------------------------------------------------- 流

    boolean hasStream(String name);

    /** 新建一个日志流，写到宿主的日志目录。 */
    boolean openLog(String name, String fileName);

    /** 新建一个文件流。宿主负责把它限制在允许的目录内。 */
    boolean openFile(String name, String path);

    boolean closeStream(String name);

    /** 设置流的默认等级（INFO/WARN/ERR/DDEBUG）。 */
    boolean setStreamLevel(String name, String level);

    /**
     * 往流里写一条。
     *
     * @param level 单条等级，{@code null} 表示用流的默认等级
     */
    void writeStream(String name, String text, String level);

    // ---------------------------------------------------------------- 诊断与宿主

    void report(CscDiagnostic diagnostic);

    /** {@code sys exit}：让当前脚本尽快结束。 */
    void exitScript();

    /**
     * 调用模块函数。模块系统下一版开放，默认报「未实现」。
     */
    default CscValue callModule(String module, String function, List<CscValue> args) {
        throw new CscRuntimeException("模块系统尚未实现：" + module + "." + function);
    }
}
