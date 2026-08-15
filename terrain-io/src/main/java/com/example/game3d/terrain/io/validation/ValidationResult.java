package com.example.game3d.terrain.io.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ValidationResult {
    private final List<ValidationProblem> problems;

    public ValidationResult(List<ValidationProblem> problems) {
        this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
    }

    public List<ValidationProblem> problems() { return problems; }

    public boolean isValid() {
        for (ValidationProblem problem : problems) {
            if (problem.severity() == ValidationProblem.Severity.ERROR) return false;
        }
        return true;
    }
}
