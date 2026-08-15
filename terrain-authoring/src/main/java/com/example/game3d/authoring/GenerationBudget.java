package com.example.game3d.authoring;

/** Independent limits for interpreting authored commands and publishing frozen segments. */
public final class GenerationBudget {
    public static final GenerationBudget UNLIMITED = new GenerationBudget(-1, -1);

    public final int commandLimit;
    public final int segmentPublishLimit;

    public GenerationBudget(int commandLimit, int segmentPublishLimit) {
        if (commandLimit < -1 || segmentPublishLimit < -1) {
            throw new IllegalArgumentException("Budgets are non-negative or -1 for unlimited");
        }
        this.commandLimit = commandLimit;
        this.segmentPublishLimit = segmentPublishLimit;
    }
}
