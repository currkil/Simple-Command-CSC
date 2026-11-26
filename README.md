# Simple Command: CSC

> 本项目的绝大部分代码由 **Deepseek Harness** 完成。

## 这是什么

**CSC**（Currkill's Simple Command Language）是 Simple Command 全家桶的**表达式与脚本层**：
指令参数里的转义、变量、表达式求值，以及脚本里的变量、列表、控制流、函数、输入输出。

它不是通用脚本语言，但比“格式化模板”多得多：CSC 有类型系统（`int` / `float` /
`string` / `bool` / `list`），有 `var` / `list` / `ctrl` / `io` / `log` / `file` / `sys`
七组语句，也有明确的错误分级。控制流与函数已经可用，模块系统与完整沙箱留给下一版。

CSC 是**纯 JDK 库**：不依赖 Minecraft、不依赖任何加载器。构建产物由 SC Core 通过
jarJar 内嵌，因此一个 Core 版本只对应一个 CSC 版本。

## 两层能力

| 层次 | 内容 |
| --- | --- |
| 表达式层 | `%变量%`、`\!(表达式)`、反斜杠转义、`//` 与 `/* */` 注释、运算与类型转换 |
| 脚本层 | `var` / `list` / `ctrl`（if / for / while / do / function）/ `io` / `log` / `file` / `sys` |

**值不再以字符串在接口间传递**：一切都是 `CscValue`，类型信息一路保留到存档。

## 设计目标

1. **可预测**：转义、注释、变量、表达式的边界由规范写死，实现与文档一致。
2. **可诊断**：错误分严重错误与隐式转换两级，记录位置明确。
3. **可扩展**：词法与求值器无副作用，模块系统与将来的脚本运行时可以复用。
4. **可移植**：只通过宿主上下文与外部交互，不写文件、不打印、不认识游戏类型。

## 它不做什么

- 不认识 Minecraft，也不认识任何加载器。
- 不决定输出到哪：聊天栏、游戏日志、日志流、文件流都由宿主给定。
- 本版本不做模块加载、不做执行超时与资源隔离（只有临时的迭代/递归上限）。

## 命名与版本

| 项目 | 值 |
| --- | --- |
| 目录 | `Simple Command CSC` |
| Gradle 工程 | `simple-command-csc` |
| Maven 坐标 | `cd.cc.currkill:sc_csc:0.2.0` |
| Java 包 | `currkill.sc_csc.api`（公共）、`currkill.sc_csc.internal`（内部） |
| 版本 | `0.2.0` |

## 与 Core 的关系

CSC 是全家桶的“粘合剂”：日志格式化、指令参数里的动态计算、将来的脚本终端，
都通过 CSC 统一。因此 **CSC 的语义稳定性优先级最高**——它一变，所有子模组都受影响。
语言的破坏性变更需要提升版本号并在规范里记录。

Core 通过 Maven 坐标依赖 CSC 并把它内嵌进自己的 jar，子模组只依赖 Core。

## 规范与文档

- 完整语法、转义表、优先级、类型规则、错误分级与示例：`LANGUAGE.md`（唯一事实来源）。
- 接口与实现细节：源码 Javadoc。
- 构建与冒烟测试：见工程根目录的 Gradle 脚本（`build` / `smokeTest` / `publishToMavenLocal`）。

## 许可与致谢

本项目采用 MIT 许可证。

本项目绝大部分代码由 **Deepseek Harness** 完成。
