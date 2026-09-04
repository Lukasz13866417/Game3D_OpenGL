package com.example.game3d.terrain.editor.compile;

@FunctionalInterface
public interface DocumentCompiler {
    CompileResult compile(CompileRequest request) throws Exception;
}
