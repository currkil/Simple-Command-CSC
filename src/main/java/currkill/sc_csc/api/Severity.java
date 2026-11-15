package currkill.sc_csc.api;

/**
 * CSC 诊断级别。
 *
 * <p>只有两级，和 SC Core 的错误分级一一对应：
 * <ul>
 *   <li>{@link #SEVERE}：语法错误、未闭合、除零、未知变量、类型不匹配导致求值失败。
 *       调用方应当提示到聊天栏，并写入游戏日志与指令日志。</li>
 *   <li>{@link #CONVERSION}：隐式转换（数值转字符串、字符串解析为数值、布尔参与数值运算）。
 *       调用方只写入指令日志，不打扰玩家。</li>
 * </ul>
 */
public enum Severity {
    SEVERE,
    CONVERSION
}
