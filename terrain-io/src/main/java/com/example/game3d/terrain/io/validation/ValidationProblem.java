package com.example.game3d.terrain.io.validation;

import java.util.Objects;

public final class ValidationProblem {
    public enum Severity { ERROR, WARNING }

    private final Severity severity;
    private final String path;
    private final String message;

    public ValidationProblem(Severity severity, String path, String message) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.path = Objects.requireNonNull(path, "path");
        this.message = Objects.requireNonNull(message, "message");
    }

    public Severity severity() { return severity; }
    public String path() { return path; }
    public String message() { return message; }

    @Override public String toString() { return severity + " " + path + ": " + message; }
}
