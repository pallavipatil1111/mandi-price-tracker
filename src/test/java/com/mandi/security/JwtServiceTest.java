package com.mandi.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mandi.domain.Role;
import com.mandi.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private static final Instant NOW = Instant.parse("2026-10-14T04:30:00Z");

    private static User user() {
        User u = new User("Pallavi", "pallavi@example.com", "hash", Role.ADMIN);
        u.setId(42L);
        return u;
    }

    @Test
    void issuedTokenVerifiesBackToTheSameUser() {
        JwtService jwt = new JwtService(SECRET, 60, Clock.fixed(NOW, ZoneOffset.UTC));
        AuthUser me = jwt.verify(jwt.issue(user())).orElseThrow();
        assertThat(me.id()).isEqualTo(42L);
        assertThat(me.email()).isEqualTo("pallavi@example.com");
        assertThat(me.isAdmin()).isTrue();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, 60, Clock.fixed(NOW, ZoneOffset.UTC));
        String token = jwt.issue(user());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(jwt.verify(tampered)).isEmpty();
        assertThat(jwt.verify("not-a-jwt")).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        String foreign = new JwtService("another-secret-another-secret-another-1", 60, clock).issue(user());
        assertThat(new JwtService(SECRET, 60, clock).verify(foreign)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = new JwtService(SECRET, 60, Clock.fixed(NOW, ZoneOffset.UTC)).issue(user());
        JwtService later = new JwtService(SECRET, 60, Clock.fixed(NOW.plus(Duration.ofMinutes(61)), ZoneOffset.UTC));
        assertThat(later.verify(token)).isEmpty();
    }
}
