package com.mandi.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.mandi.domain.Alert;
import com.mandi.domain.Commodity;
import com.mandi.domain.Market;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertRuleTest {

    private static final Instant NOW = Instant.parse("2026-10-14T14:00:00Z");

    private static Alert alert(AlertDirection direction, int threshold) {
        return new Alert(1L, new Commodity("Onion", "Vegetables"), new Market("Maharashtra", "Nashik", "Lasalgaon"),
                direction, threshold, NOW);
    }

    @Test
    void decisionTable() {
        assertThat(AlertRule.decide(true, true, false)).isEqualTo(AlertRule.Decision.FIRE);
        assertThat(AlertRule.decide(false, true, false)).isEqualTo(AlertRule.Decision.NONE);
        assertThat(AlertRule.decide(false, false, false)).isEqualTo(AlertRule.Decision.NONE); // just below: not yet
        assertThat(AlertRule.decide(false, false, true)).isEqualTo(AlertRule.Decision.REARM);
        assertThat(AlertRule.decide(true, false, true)).isEqualTo(AlertRule.Decision.NONE);
    }

    @Test
    void firesOnceOnTheCrossingThenWaitsForThePriceToComeBack() {
        Alert a = alert(AlertDirection.ABOVE, 2500);
        a.armFor(2300);
        assertThat(a.observe(2400, NOW)).isFalse();
        assertThat(a.observe(2550, NOW)).isTrue();   // crossed
        assertThat(a.getLastTriggeredAt()).isEqualTo(NOW);
        assertThat(a.observe(2600, NOW)).isFalse();  // still above: no repeat notification
        assertThat(a.observe(2700, NOW)).isFalse();
        assertThat(a.observe(2480, NOW)).isFalse();  // dipped just below: noise, still disarmed
        assertThat(a.observe(2510, NOW)).isFalse();  // so hovering around ₹2,500 does not spam
        assertThat(a.isArmed()).isFalse();
        assertThat(a.observe(2440, NOW)).isFalse();  // clearly back below (2% = ₹50): re-armed
        assertThat(a.isArmed()).isTrue();
        assertThat(a.observe(2500, NOW)).isTrue();   // threshold itself counts as reached
    }

    @Test
    void belowAlertsWatchForFalls() {
        Alert a = alert(AlertDirection.BELOW, 1500);
        a.armFor(1700);
        assertThat(a.observe(1600, NOW)).isFalse();
        assertThat(a.observe(1500, NOW)).isTrue();
        assertThat(a.observe(1400, NOW)).isFalse();
        assertThat(a.observe(1520, NOW)).isFalse(); // within 2% (₹30): still disarmed
        assertThat(a.observe(1540, NOW)).isFalse(); // re-armed
        assertThat(a.observe(1490, NOW)).isTrue();
    }

    @Test
    void anAlertCreatedWhenThePriceIsAlreadyPastTheThresholdWaitsForAFreshCrossing() {
        Alert a = alert(AlertDirection.ABOVE, 2000);
        a.armFor(2200);
        assertThat(a.isArmed()).isFalse();
        assertThat(a.observe(2300, NOW)).isFalse();
        assertThat(a.observe(1900, NOW)).isFalse();
        assertThat(a.observe(2050, NOW)).isTrue();
        assertThat(AlertDirection.ABOVE.hasReset(1959, 2000)).isTrue();
        assertThat(AlertDirection.ABOVE.hasReset(1960, 2000)).isFalse();
    }

    @Test
    void rupeesUseIndianGrouping() {
        assertThat(Money.rupees(999)).isEqualTo("₹999");
        assertThat(Money.rupees(2150)).isEqualTo("₹2,150");
        assertThat(Money.rupees(125000)).isEqualTo("₹1,25,000");
        assertThat(Money.rupees(12345678)).isEqualTo("₹1,23,45,678");
        assertThat(Money.rupees(-1500)).isEqualTo("-₹1,500");
    }
}
