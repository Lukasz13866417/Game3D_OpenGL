package com.example.game3d.terrain.io.publish;

import com.example.game3d.terrain.io.validation.ValidationProblem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PublishException extends Exception {
    private final List<ValidationProblem> problems;

    public PublishException(String message) {
        this(message, Collections.<ValidationProblem>emptyList(), null);
    }

    public PublishException(String message, Throwable cause) {
        this(message, Collections.<ValidationProblem>emptyList(), cause);
    }

    public PublishException(String message, List<ValidationProblem> problems) {
        this(message, problems, null);
    }

    private PublishException(String message, List<ValidationProblem> problems, Throwable cause) {
        super(message, cause);
        this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
    }

    public List<ValidationProblem> problems() { return problems; }
}
