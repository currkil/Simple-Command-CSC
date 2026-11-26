package currkill.sc_csc.api;

/** 语法错误：括号/引号未闭合、记号无法识别、语句形式非法等。属于严重错误。 */
public class CscSyntaxException extends CscRuntimeException {

    private final int position;

    public CscSyntaxException(String message) {
        this(message, -1);
    }

    public CscSyntaxException(String message, int position) {
        super(message);
        this.position = position;
    }

    /** 出错位置（字符偏移），-1 表示未知。 */
    public int position() {
        return position;
    }
}
