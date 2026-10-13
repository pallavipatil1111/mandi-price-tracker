package com.mandi.core;

public enum AlertDirection {
    /** Notify when the modal price reaches the threshold or goes higher (a farmer waiting to sell). */
    ABOVE,
    /** Notify when the modal price falls to the threshold or lower (a trader or buyer waiting to buy). */
    BELOW;

    /** Share of the threshold the price must move back by before a fired alert can fire again. */
    public static final double REARM_MARGIN = 0.02;

    public boolean isMet(int price, int threshold) {
        return this == ABOVE ? price >= threshold : price <= threshold;
    }

    /**
     * True once the price has moved clearly back to the other side (2% past the threshold). Without
     * this hysteresis, a price hovering around ₹2,500 would fire an "above ₹2,500" alert day after day.
     */
    public boolean hasReset(int price, int threshold) {
        int margin = Math.max(1, (int) Math.round(threshold * REARM_MARGIN));
        return this == ABOVE ? price < threshold - margin : price > threshold + margin;
    }
}
