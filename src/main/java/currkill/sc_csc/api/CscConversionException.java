package currkill.sc_csc.api;

/**
 * 类型转换失败。
 *
 * <p>这是「严重错误」的来源之一：由调用方捕获后决定提示方式。CSC 自己不会吞掉它。
 */
public class CscConversionException extends CscRuntimeException {

    private final CscType from;
    private final CscType to;

    public CscConversionException(CscType from, CscType to) {
        super("无法把 " + from.keyword() + " 转换为 " + to.keyword());
        this.from = from;
        this.to = to;
    }

    public CscConversionException(CscType from, CscType to, String detail) {
        super("无法把 " + from.keyword() + " 转换为 " + to.keyword() + "：" + detail);
        this.from = from;
        this.to = to;
    }

    public CscType from() {
        return from;
    }

    public CscType to() {
        return to;
    }
}
