package currkill.sc_csc.api;

/** 一个可被脚本调用的函数。模块系统在下一版开放，本接口先定型。 */
@FunctionalInterface
public interface CscFunction {

    /**
     * @param args 实参，已按调用顺序排好
     * @param ctx  执行上下文
     * @return 返回值，无返回值时返回 {@link CscValue#ofVoid()}
     */
    CscValue apply(java.util.List<CscValue> args, CscContext ctx);
}
