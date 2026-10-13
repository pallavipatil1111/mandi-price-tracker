package com.mandi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mandi.config.StartupDataAccess;
import com.mandi.core.AlertDirection;
import com.mandi.web.dto.UserViews;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoAlertTest {

    @Test
    void demoAlertsAreSetWhereTheComingMonthWillCrossThem() {
        UserViews.AlertRequest up = StartupDataAccess.demoAlert(2000, List.of(2100, 2600, 2400), false);
        assertThat(up.direction()).isEqualTo(AlertDirection.ABOVE);
        assertThat(up.threshold()).isEqualTo(2360); // 60% of the way from 2,000 to the 2,600 peak

        UserViews.AlertRequest down = StartupDataAccess.demoAlert(2000, List.of(1900, 1500, 2050), false);
        assertThat(down.direction()).isEqualTo(AlertDirection.BELOW);
        assertThat(down.threshold()).isEqualTo(1700);

        // small moves: still strictly beyond today's price and never past the extreme
        assertThat(StartupDataAccess.demoAlert(2000, List.of(2004, 1999), false).threshold()).isEqualTo(2001);
        assertThat(StartupDataAccess.demoAlert(2000, List.of(1997), false).threshold()).isEqualTo(1999);

        UserViews.AlertRequest waiting = StartupDataAccess.demoAlert(2000, List.of(2100, 2200), true);
        assertThat(waiting.threshold()).isEqualTo(2450); // 12% above the month's high: out of reach
    }
}
