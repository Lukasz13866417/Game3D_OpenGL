package com.example.game3d.core.simulation;

public final class JumpDecision {
    public enum Action {
        JUMP_NOW,
        DEFER,
        REJECT
    }

    public final JumpRuleId rule;
    public final Action action;
    public final boolean consumesAirCharge;
    public final String reason;

    public JumpDecision(JumpRuleId rule, Action action,
                        boolean consumesAirCharge, String reason) {
        this.rule = rule;
        this.action = action;
        this.consumesAirCharge = consumesAirCharge;
        this.reason = reason;
    }
}
