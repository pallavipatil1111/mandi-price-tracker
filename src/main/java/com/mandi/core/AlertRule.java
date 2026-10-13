package com.mandi.core;

/**
 * Edge-triggered alerting with hysteresis. A level-triggered alert ("price is above ₹2,500") would
 * notify every day the price stays high. Instead an alert fires once when the condition becomes true,
 * disarms, and re-arms only after the price has moved clearly back (see {@link AlertDirection#hasReset}).
 */
public final class AlertRule {

    public enum Decision {
        /** Condition just became true: notify the user and disarm. */
        FIRE,
        /** The price moved clearly back: re-arm so the next crossing notifies. */
        REARM,
        /** Nothing changed. */
        NONE
    }

    private AlertRule() {
    }

    public static Decision decide(boolean armed, boolean conditionMet, boolean reset) {
        if (armed && conditionMet) {
            return Decision.FIRE;
        }
        if (!armed && reset) {
            return Decision.REARM;
        }
        return Decision.NONE;
    }
}
