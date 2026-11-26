package currkill.sc_csc.internal;

import currkill.sc_csc.api.CscType;

import java.util.List;

/**
 * 脚本语句的抽象表示。
 *
 * <p>所有含表达式的字段都保存**源码文本**而不是求值结果：循环条件、循环体每次执行
 * 都会重新求值，这样变量变化才能生效，也让 AST 保持无状态。
 */
public sealed interface Stmt {

    record VarNew(String name, CscType type, String expr) implements Stmt {
    }

    record VarSet(String name, String expr) implements Stmt {
    }

    record VarDelete(String name) implements Stmt {
    }

    record ListNew(String name, CscType elementType, List<String> valueExprs, Integer declaredSize) implements Stmt {
    }

    record ListNewv(String name, CscType elementType, String expr) implements Stmt {
    }

    record ListDelete(String name) implements Stmt {
    }

    record ListResize(String name, String expr) implements Stmt {
    }

    record Branch(String condition, List<Stmt> body) {
    }

    record If(List<Branch> branches, List<Stmt> elseBody) implements Stmt {
    }

    record For(Stmt init, String condition, Stmt step, List<Stmt> body) implements Stmt {
    }

    record While(String condition, List<Stmt> body) implements Stmt {
    }

    record DoWhile(List<Stmt> body, String condition) implements Stmt {
    }

    record FuncDecl(String name, List<String> params, List<Stmt> body) implements Stmt {
    }

    record FuncCall(String name, List<String> argExprs) implements Stmt {
    }

    record Return(String expr) implements Stmt {
    }

    record Cout(String content, String stream, String level) implements Stmt {
    }

    record Cerr(String content, String stream, String level) implements Stmt {
    }

    record LogNew(String name, String fileName) implements Stmt {
    }

    record LogOpen(String name, String fileName) implements Stmt {
    }

    record LogClose(String name) implements Stmt {
    }

    record LogLevel(String name, String level) implements Stmt {
    }

    record FileNew(String name, String path) implements Stmt {
    }

    record FileOpen(String name, String path) implements Stmt {
    }

    record FileClose(String name) implements Stmt {
    }

    record SysExit() implements Stmt {
    }

    /** 本版本尚未实现的能力（模块加载、cin/cinf），执行时报严重错误。 */
    record Unsupported(String feature) implements Stmt {
    }

    /** 空语句，用于允许空的 for 初始化/迭代段。 */
    record Nop() implements Stmt {
    }
}
