package com.mandi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mandi.core.AlertDirection;
import com.mandi.domain.Alert;
import com.mandi.domain.Notification;
import com.mandi.repository.AlertRepository;
import com.mandi.repository.NotificationRepository;
import com.mandi.web.dto.UserViews;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertServiceTest {

    private Fixture f;
    private final AlertRepository alerts = mock(AlertRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final List<Alert> saved = new ArrayList<>();
    private final List<Notification> sent = new ArrayList<>();
    private AlertService service;

    @BeforeEach
    void setUp() {
        f = new Fixture();
        when(alerts.save(any())).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId((long) saved.size() + 1);
            saved.add(a);
            return a;
        });
        when(alerts.findActiveForSeries(any(), any())).thenAnswer(inv -> saved.stream().filter(Alert::isActive)
                .filter(a -> a.getCommodity().getId().equals(inv.getArgument(0)) && a.getMarket().getId().equals(inv.getArgument(1)))
                .toList());
        when(alerts.findByIdAndUserId(any(), any())).thenAnswer(inv -> saved.stream()
                .filter(a -> a.getId().equals(inv.getArgument(0)) && a.getUserId().equals(inv.getArgument(1))).findFirst());
        when(notifications.save(any())).thenAnswer(inv -> {
            sent.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        service = new AlertService(alerts, notifications, f.query(), Fixture.CLOCK);
    }

    private UserViews.AlertRequest onionAbove(int threshold) {
        return new UserViews.AlertRequest(f.onion.getId(), f.lasalgaon.getId(), AlertDirection.ABOVE, threshold);
    }

    private int evaluateWithNewPrice(int modal) {
        f.prices(f.onion, f.lasalgaon, f.series.get(new SeriesKey(1L, 10L)).getLast().date().plusDays(1), modal);
        return service.evaluate(Set.of(new SeriesKey(f.onion.getId(), f.lasalgaon.getId())));
    }

    @Test
    void createdAlertsShowTheCurrentPrice() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, 2200);

        UserViews.AlertView view = service.create(7L, onionAbove(2500));

        assertThat(view.currentPrice()).isEqualTo(2200);
        assertThat(view.conditionMet()).isFalse();
        assertThat(view.armed()).isTrue();
        assertThat(view.active()).isTrue();
    }

    @Test
    void anImportThatCrossesTheThresholdNotifiesOnce() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY.minusDays(3), 2200);
        service.create(7L, onionAbove(2500));

        assertThat(evaluateWithNewPrice(2400)).isZero();
        assertThat(evaluateWithNewPrice(2560)).isEqualTo(1);
        assertThat(evaluateWithNewPrice(2610)).isZero();

        assertThat(sent).hasSize(1);
        Notification n = sent.get(0);
        assertThat(n.getUserId()).isEqualTo(7L);
        assertThat(n.getPrice()).isEqualTo(2560);
        assertThat(n.getPriceDate()).isEqualTo(Fixture.TODAY.minusDays(1));
        assertThat(n.getMessage()).isEqualTo("Onion at Lasalgaon rose to ₹2,560/quintal on 13 Oct (your alert: above ₹2,500)");
    }

    @Test
    void pausedAlertsStaySilentAndResumeAgainstTodaysPrice() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY.minusDays(3), 2200);
        UserViews.AlertView created = service.create(7L, onionAbove(2500));
        service.setActive(7L, created.id(), false);

        assertThat(evaluateWithNewPrice(2600)).isZero();

        UserViews.AlertView resumed = service.setActive(7L, created.id(), true);
        assertThat(resumed.conditionMet()).isTrue();
        assertThat(resumed.armed()).isFalse(); // already above: waits for a fresh crossing
        assertThat(evaluateWithNewPrice(2650)).isZero();
    }

    @Test
    void alertsBelongToTheirOwner() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, 2200);
        UserViews.AlertView mine = service.create(7L, onionAbove(2500));

        assertThatThrownBy(() -> service.delete(8L, mine.id())).isInstanceOf(ApiException.class).hasMessage("Alert not found");
        verify(alerts, never()).delete(any());
    }

    @Test
    void rejectsAlertsForSeriesWithoutPricesAndCapsTheCount() {
        assertThatThrownBy(() -> service.create(7L, onionAbove(2500))).isInstanceOf(ApiException.class);

        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, 2200);
        when(alerts.countByUserId(7L)).thenReturn((long) AlertService.MAX_ALERTS);
        assertThatThrownBy(() -> service.create(7L, onionAbove(2500))).hasMessageContaining("up to 20 alerts");
    }

    @Test
    void belowAlertsDescribeAFall() {
        f.prices(f.tomato, f.pune, Fixture.TODAY.minusDays(1), 1800);
        Alert a = new Alert(7L, f.tomato, f.pune, AlertDirection.BELOW, 1500, Fixture.CLOCK.instant());
        assertThat(AlertService.message(a, new com.mandi.core.PricePoint(Fixture.TODAY, 1300, 1600, 1450)))
                .isEqualTo("Tomato at Pune fell to ₹1,450/quintal on 14 Oct (your alert: below ₹1,500)");
        when(alerts.findByIdAndUserId(1L, 7L)).thenReturn(Optional.empty());
    }
}
