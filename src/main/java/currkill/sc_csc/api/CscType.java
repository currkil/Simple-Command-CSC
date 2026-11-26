package currkill.sc_csc.api;

import java.util.Locale;
import java.util.Optional;

/**
 * CSC 的类型集合（半 C++ 风格）。
 *
 * <p>变量声明必须带类型；值本身也带类型。两者不同时按隐式转换规则处理，
 * 合法转换记一条隐式转换诊断，非法转换报严重错误。
 */
public enum CscType {

    /** 整数，64 位。 */
    INT("int"),
    /** 浮点，双精度。 */
    FLOAT("float"),
    /** 字符串。 */
    STRING("string"),
    /** 布尔。 */
    BOOL("bool"),
    /** 列表。 */
    LIST("list"),
    /** 模块（模块系统尚未实现，保留类型）。 */
    MODULE("module"),
    /** 无返回值。 */
    VOID("void");

    private final String keyword;

    CscType(String keyword) {
        this.keyword = keyword;
    }

    /** 脚本里写的类型名。 */
    public String keyword() {
        return keyword;
    }

    /** 解析类型名，大小写不敏感；{@code auto} 表示需要从值推断。 */
    public static Optional<CscType> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        for (CscType type : values()) {
            if (type.keyword.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public boolean isNumeric() {
        return this == INT || this == FLOAT;
    }
}
