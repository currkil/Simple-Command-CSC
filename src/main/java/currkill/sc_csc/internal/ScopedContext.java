package currkill.sc_csc.internal;

import currkill.sc_csc.api.CscContext;
import currkill.sc_csc.api.CscDiagnostic;
import currkill.sc_csc.api.CscType;
import currkill.sc_csc.api.CscValue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 带调用帧的上下文代理。
 *
 * <p>函数形参与函数内新声明的变量落在当前帧里，函数返回即消失；其余读写一律转发给宿主。
 * CSC 本体没有块级作用域，只有“全局 + 当前调用帧”两层。
 */
final class ScopedContext implements CscContext {

    private final CscContext base;
    private final Deque<Map<String, CscValue>> frames = new ArrayDeque<>();
    private final Deque<Map<String, CscType>> frameTypes = new ArrayDeque<>();

    ScopedContext(CscContext base) {
        this.base = base;
    }

    void push(Map<String, CscValue> values, Map<String, CscType> types) {
        frames.push(values);
        frameTypes.push(types);
    }

    void pop() {
        frames.pop();
        frameTypes.pop();
    }

    private Map<String, CscValue> frame() {
        return frames.peek();
    }

    @Override
    public boolean hasVariable(String name) {
        Map<String, CscValue> frame = frame();
        return (frame != null && frame.containsKey(name)) || base.hasVariable(name);
    }

    @Override
    public CscValue getVariable(String name) {
        Map<String, CscValue> frame = frame();
        if (frame != null && frame.containsKey(name)) {
            return frame.get(name);
        }
        return base.getVariable(name);
    }

    @Override
    public CscType declaredType(String name) {
        Map<String, CscType> types = frameTypes.peek();
        if (types != null && types.containsKey(name)) {
            return types.get(name);
        }
        return base.declaredType(name);
    }

    @Override
    public void declareVariable(String name, CscType type, CscValue initial) {
        Map<String, CscValue> frame = frame();
        if (frame != null) {
            frame.put(name, initial);
            frameTypes.peek().put(name, type);
        } else {
            base.declareVariable(name, type, initial);
        }
    }

    @Override
    public void setVariable(String name, CscValue value) {
        Map<String, CscValue> frame = frame();
        if (frame != null && frame.containsKey(name)) {
            frame.put(name, value);
            return;
        }
        base.setVariable(name, value);
    }

    @Override
    public void deleteVariable(String name) {
        Map<String, CscValue> frame = frame();
        if (frame != null && frame.remove(name) != null) {
            frameTypes.peek().remove(name);
            return;
        }
        base.deleteVariable(name);
    }

    @Override
    public Set<String> variableNames() {
        Map<String, CscValue> frame = frame();
        if (frame == null || frame.isEmpty()) {
            return base.variableNames();
        }
        java.util.Set<String> merged = new java.util.LinkedHashSet<>(base.variableNames());
        merged.addAll(frame.keySet());
        return merged;
    }

    @Override
    public void cout(String text) {
        base.cout(text);
    }

    @Override
    public void cerr(String text) {
        base.cerr(text);
    }

    @Override
    public boolean hasStream(String name) {
        return base.hasStream(name);
    }

    @Override
    public boolean openLog(String name, String fileName) {
        return base.openLog(name, fileName);
    }

    @Override
    public boolean openFile(String name, String path) {
        return base.openFile(name, path);
    }

    @Override
    public boolean closeStream(String name) {
        return base.closeStream(name);
    }

    @Override
    public boolean setStreamLevel(String name, String level) {
        return base.setStreamLevel(name, level);
    }

    @Override
    public void writeStream(String name, String text, String level) {
        base.writeStream(name, text, level);
    }

    @Override
    public void report(CscDiagnostic diagnostic) {
        base.report(diagnostic);
    }

    @Override
    public void exitScript() {
        base.exitScript();
    }

    @Override
    public CscValue callModule(String module, String function, List<CscValue> args) {
        return base.callModule(module, function, args);
    }

    static Map<String, CscValue> newFrame() {
        return new HashMap<>();
    }

    static Map<String, CscType> newTypeFrame() {
        return new HashMap<>();
    }
}
