package currkill.sc_csc.api;

/** 诊断级别。和 SC Core 的错误分级一一对应。 */
public enum CscSeverity {

    /** 严重错误：语法错误、未闭合、除零、未知变量、转换失败、越界、模块加载失败。 */
    SEVERE,

    /** 隐式转换：合法但值得记录的转换。 */
    CONVERSION
}
