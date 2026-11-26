package currkill.sc_csc.api;

/**
 * 运行时值。所有变量、表达式结果、函数返回值统一用本类型传输，不再以裸字符串传递。
 *
 * <p>取用方式分两类：
 * <ul>
 *   <li>{@link #raw()} 拿原始对象，不做转换；</li>
 *   <li>{@code asInt} / {@code asFloat} / {@code asString} / {@code asBool} 做转换，
 *       失败抛 {@link CscConversionException}，由调用方决定错误级别。</li>
 * </ul>
 *
 * <p>列表的 {@code raw()} 是 {@code List<CscValue>}。
 */
public final class CscValue {

    private final CscType type;
    private final Object raw;

    private CscValue(CscType type, Object raw) {
        this.type = type;
        this.raw = raw;
    }

    public static CscValue ofInt(long value) {
        return new CscValue(CscType.INT, value);
    }

    public static CscValue ofFloat(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new CscRuntimeException("浮点结果不是有限数");
        }
        return new CscValue(CscType.FLOAT, value);
    }

    public static CscValue ofString(String value) {
        return new CscValue(CscType.STRING, value == null ? "" : value);
    }

    public static CscValue ofBool(boolean value) {
        return new CscValue(CscType.BOOL, value);
    }

    public static CscValue ofList(java.util.List<CscValue> values) {
        return new CscValue(CscType.LIST, java.util.List.copyOf(values));
    }

    public static CscValue ofVoid() {
        return new CscValue(CscType.VOID, null);
    }

    public CscType type() {
        return type;
    }

    public Object raw() {
        return raw;
    }

    public boolean isNumeric() {
        return type.isNumeric();
    }

    public boolean isVoid() {
        return type == CscType.VOID;
    }

    @SuppressWarnings("unchecked")
    public java.util.List<CscValue> asList() {
        if (type != CscType.LIST) {
            throw new CscConversionException(type, CscType.LIST);
        }
        return (java.util.List<CscValue>) raw;
    }

    public long asInt() {
        return switch (type) {
            case INT -> (Long) raw;
            case FLOAT -> (long) (double) (Double) raw;
            case BOOL -> (Boolean) raw ? 1L : 0L;
            case STRING -> parseLong((String) raw);
            default -> throw new CscConversionException(type, CscType.INT);
        };
    }

    public double asFloat() {
        return switch (type) {
            case FLOAT -> (Double) raw;
            case INT -> (double) (Long) raw;
            case BOOL -> (Boolean) raw ? 1.0D : 0.0D;
            case STRING -> parseDouble((String) raw);
            default -> throw new CscConversionException(type, CscType.FLOAT);
        };
    }

    public boolean asBool() {
        return switch (type) {
            case BOOL -> (Boolean) raw;
            case INT -> (Long) raw != 0L;
            case FLOAT -> (Double) raw != 0.0D;
            case STRING -> parseBool((String) raw);
            default -> throw new CscConversionException(type, CscType.BOOL);
        };
    }

    /** 输出成文本时的样子：字符串原样，int 不带小数点，float 去尾，bool 为 true/false。 */
    public String asString() {
        return switch (type) {
            case STRING -> (String) raw;
            case INT -> Long.toString((Long) raw);
            case FLOAT -> formatDouble((Double) raw);
            case BOOL -> (Boolean) raw ? "true" : "false";
            case LIST -> {
                StringBuilder sb = new StringBuilder("[");
                java.util.List<CscValue> values = asList();
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(values.get(i).asString());
                }
                yield sb.append(']').toString();
            }
            case VOID -> "";
            case MODULE -> "<module>";
        };
    }

    /** 列表元素个数；非列表返回 -1。 */
    public int size() {
        return type == CscType.LIST ? asList().size() : -1;
    }

    @Override
    public String toString() {
        return type.keyword() + "(" + asString() + ")";
    }

    // ---------------------------------------------------------------- 内部工具

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(text.trim());
            } catch (NumberFormatException nested) {
                throw new CscConversionException(CscType.STRING, CscType.INT);
            }
        }
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new CscConversionException(CscType.STRING, CscType.FLOAT);
        }
    }

    private static boolean parseBool(String text) {
        String trimmed = text.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        throw new CscConversionException(CscType.STRING, CscType.BOOL);
    }

    public static String formatDouble(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 9.007199254740992E15D) {
            return Long.toString((long) value);
        }
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
