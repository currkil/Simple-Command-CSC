package currkill.sc_csc.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * CSC 消费的变量来源。
 *
 * <p>只要求实现 {@link #get(String)}，因此它天然是一个函数接口：文本格式化器只读不写。
 * {@code set} / {@code delete} 默认抛异常，表示只读来源；SC Core 会提供可写实现。
 *
 * <p>作用域（局部变量）不需要改本接口：扩展层包一层 {@code VariableProvider} 即可。
 */
@FunctionalInterface
public interface VariableProvider {

    /** 变量名的完整内容（已去掉两侧的 %）。不存在时返回 {@link Optional#empty()}。 */
    Optional<String> get(String name);

    default void set(String name, String value) {
        throw new UnsupportedOperationException("只读的 VariableProvider 不支持写入：" + name);
    }

    default void delete(String name) {
        throw new UnsupportedOperationException("只读的 VariableProvider 不支持删除：" + name);
    }

    /** 永远取不到值的空来源。 */
    static VariableProvider empty() {
        return name -> Optional.empty();
    }

    /** 基于 Map 的只读来源，主要给测试和脚本终端用。 */
    static VariableProvider ofMap(Map<String, String> values) {
        Map<String, String> copy = new LinkedHashMap<>(values);
        return name -> Optional.ofNullable(copy.get(name));
    }
}
