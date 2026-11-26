package currkill.sc_csc.api;

import java.util.Map;

/**
 * 模块：一组可被脚本按 {@code 模块名.函数名(...)} 调用的函数。
 *
 * <p>模块系统在下一版开放，本接口先定型，第三方 JAR 只需实现它并注册到宿主即可。
 */
public interface CscModule {

    /** 模块名，脚本里用它当前缀。 */
    String getName();

    /** 函数名 → 函数实现。 */
    Map<String, CscFunction> getFunctions();
}
