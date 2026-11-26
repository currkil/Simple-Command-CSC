package currkill.sc_csc.api;

/** CSC 运行时错误的基类。 */
public class CscRuntimeException extends RuntimeException {

    public CscRuntimeException(String message) {
        super(message);
    }

    public CscRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
