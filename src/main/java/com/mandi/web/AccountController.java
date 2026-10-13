package com.mandi.web;

import com.mandi.security.AuthUser;
import com.mandi.service.AlertService;
import com.mandi.service.WatchlistService;
import com.mandi.web.dto.UserViews;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Signed-in features. Every call is scoped to the caller from the JWT, never to an id in the request. */
@RestController
public class AccountController {

    private final WatchlistService watchlist;
    private final AlertService alerts;

    public AccountController(WatchlistService watchlist, AlertService alerts) {
        this.watchlist = watchlist;
        this.alerts = alerts;
    }

    @GetMapping("/api/watchlist")
    public List<UserViews.WatchCard> watchlist(@AuthenticationPrincipal AuthUser me) {
        return watchlist.list(me.id());
    }

    @PostMapping("/api/watchlist")
    @ResponseStatus(HttpStatus.CREATED)
    public UserViews.WatchCard watch(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UserViews.WatchRequest request) {
        return watchlist.add(me.id(), request);
    }

    @DeleteMapping("/api/watchlist/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unwatch(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        watchlist.remove(me.id(), id);
    }

    @GetMapping("/api/alerts")
    public List<UserViews.AlertView> alerts(@AuthenticationPrincipal AuthUser me) {
        return alerts.list(me.id());
    }

    @PostMapping("/api/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    public UserViews.AlertView createAlert(@AuthenticationPrincipal AuthUser me,
                                           @Valid @RequestBody UserViews.AlertRequest request) {
        return alerts.create(me.id(), request);
    }

    @PatchMapping("/api/alerts/{id}")
    public UserViews.AlertView toggleAlert(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                           @Valid @RequestBody UserViews.AlertToggle request) {
        return alerts.setActive(me.id(), id, request.active());
    }

    @DeleteMapping("/api/alerts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlert(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        alerts.delete(me.id(), id);
    }

    @GetMapping("/api/notifications")
    public UserViews.Inbox notifications(@AuthenticationPrincipal AuthUser me) {
        return alerts.inbox(me.id());
    }

    @PostMapping("/api/notifications/read")
    public Map<String, Integer> markRead(@AuthenticationPrincipal AuthUser me) {
        return Map.of("marked", alerts.markAllRead(me.id()));
    }
}
