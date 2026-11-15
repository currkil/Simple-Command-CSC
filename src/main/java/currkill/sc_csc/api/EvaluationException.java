package currkill.sc_csc.api;

/**
 * 求值过程中的致命错误。
 *
 * <p>这是可捕获的：指令层捕获后「保留原样 + 记日志」，脚本层捕获后转成脚本异常。
 * 求值器本身不打印任何东西，也不依赖 Minecraft 类型。
 */
public class EvaluationException extends RuntimeException {

    public EvaluationException(String message) {
        super(message);
    }

    public EvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
