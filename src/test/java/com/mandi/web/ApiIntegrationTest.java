package com.mandi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole application over HTTP: Flyway schema, a year of demo history loaded through the real import
 * pipeline at startup, JWT security, validation, and the alert flow. Runs on H2 and on MySQL 8 in CI.
 */
@SpringBootTest(properties = {"app.demo-data=true", "app.import.enabled=false"})
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private JsonNode getJson(String url, String auth) throws Exception {
        var request = get(url);
        if (auth != null) {
            request = request.header("Authorization", auth);
        }
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private String newUser() throws Exception {
        String email = "u-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Trader", "email", email, "password", "secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"));
        return login(email, "secret123");
    }

    /** [commodityId, marketId] looked up by name in the public catalogue. */
    private long[] ids(String commodity, String market) throws Exception {
        for (JsonNode c : getJson("/api/prices/catalog", null).get("commodities")) {
            if (c.get("name").asText().equals(commodity)) {
                for (JsonNode m : c.get("markets")) {
                    if (m.get("name").asText().equals(market)) {
                        return new long[] {c.get("id").asLong(), m.get("id").asLong()};
                    }
                }
            }
        }
        throw new AssertionError(commodity + " in " + market + " not in catalogue");
    }

    @Test
    void demoHistoryIsLoadedAndPricesArePublic() throws Exception {
        JsonNode catalog = getJson("/api/prices/catalog", null);
        assertThat(catalog.get("commodities").size()).isEqualTo(10);

        JsonNode board = getJson("/api/prices/board", null);
        assertThat(board.get("rows").size()).isEqualTo(27);
        assertThat(board.get("lastImport").get("source").asText()).isEqualTo("sample");

        long[] onion = ids("Onion", "Lasalgaon");
        JsonNode series = getJson("/api/prices/series?days=90&commodityId=" + onion[0] + "&marketId=" + onion[1], null);
        assertThat(series.get("points").size()).isBetween(65, 80); // ~90 days minus Sundays and gaps
        assertThat(series.get("points").get(0).get("ma7").isNull()).isFalse();
        assertThat(series.get("stats").get("tradingDays30").asInt()).isGreaterThan(20);

        JsonNode compare = getJson("/api/prices/compare?commodityId=" + onion[0], null);
        assertThat(compare.get("markets").size()).isEqualTo(5);
        assertThat(compare.get("spread").asInt()).isPositive();

        JsonNode monthly = getJson("/api/prices/monthly?commodityId=" + onion[0] + "&marketId=" + onion[1], null);
        assertThat(monthly.get("months").size()).isBetween(12, 13);

        String csv = mvc.perform(get("/api/prices/export.csv").param("commodityId", "" + onion[0])
                        .param("marketId", "" + onion[1]).param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Onion-Lasalgaon-30d.csv\""))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("date,commodity,market,district,state,min_price,max_price,modal_price,moving_avg_7");
    }

    @Test
    void badQueriesGetClearErrors() throws Exception {
        long[] onion = ids("Onion", "Lasalgaon");
        mvc.perform(get("/api/prices/series").param("commodityId", "" + onion[0]).param("marketId", "" + onion[1])
                        .param("days", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("days must be between 7 and 1095"));
        mvc.perform(get("/api/prices/series").param("commodityId", "999999").param("marketId", "" + onion[1]))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/prices/series").param("commodityId", "abc").param("marketId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signedInFeaturesAndAdminRoutesAreProtected() throws Exception {
        mvc.perform(get("/api/watchlist")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/imports")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/imports").header("Authorization", newUser())).andExpect(status().isForbidden());
    }

    @Test
    void demoUserReceivedRealNotificationsDuringTheLastMonthOfImports() throws Exception {
        String demo = login("demo@mandi.in", "demo1234");

        assertThat(getJson("/api/watchlist", demo).size()).isEqualTo(5);
        JsonNode alerts = getJson("/api/alerts", demo);
        assertThat(alerts.size()).isEqualTo(5);
        int fired = 0;
        for (JsonNode a : alerts) {
            if (a.get("commodity").asText().equals("Cotton")) {
                assertThat(a.get("lastTriggeredAt").isNull()).isTrue(); // set out of reach: still waiting
            } else if (!a.get("lastTriggeredAt").isNull()) {
                fired++;
            }
        }
        assertThat(fired).isEqualTo(4);

        JsonNode inbox = getJson("/api/notifications", demo);
        assertThat(inbox.get("unread").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(inbox.get("items").get(0).get("message").asText()).contains("/quintal on ");

        mvc.perform(post("/api/notifications/read").header("Authorization", demo)).andExpect(status().isOk());
        assertThat(getJson("/api/notifications", demo).get("unread").asInt()).isZero();
    }

    @Test
    void watchlistAndAlertLifecycle() throws Exception {
        String user = newUser();
        long[] tomato = ids("Tomato", "Pune");
        String watch = json.writeValueAsString(Map.of("commodityId", tomato[0], "marketId", tomato[1]));

        JsonNode card = json.readTree(mvc.perform(post("/api/watchlist").header("Authorization", user)
                        .contentType(MediaType.APPLICATION_JSON).content(watch))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(card.get("latest").get("modal").asInt()).isPositive();
        mvc.perform(post("/api/watchlist").header("Authorization", user).contentType(MediaType.APPLICATION_JSON).content(watch))
                .andExpect(status().isConflict());

        int current = card.get("latest").get("modal").asInt();
        // An alert the current price already satisfies starts disarmed: no instant notification.
        JsonNode alert = json.readTree(mvc.perform(post("/api/alerts").header("Authorization", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("commodityId", tomato[0], "marketId", tomato[1],
                                "direction", "ABOVE", "threshold", current - 100))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(alert.get("conditionMet").asBoolean()).isTrue();
        assertThat(alert.get("armed").asBoolean()).isFalse();
        long alertId = alert.get("id").asLong();

        mvc.perform(post("/api/alerts").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("commodityId", tomato[0], "marketId", tomato[1],
                                "direction", "SIDEWAYS", "threshold", 100))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/alerts").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("commodityId", tomato[0], "marketId", tomato[1],
                                "direction", "BELOW", "threshold", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.threshold").exists());

        String stranger = newUser();
        mvc.perform(delete("/api/alerts/" + alertId).header("Authorization", stranger)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/alerts/" + alertId).header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(delete("/api/alerts/" + alertId).header("Authorization", user)).andExpect(status().isNoContent());
        assertThat(getJson("/api/alerts", user).size()).isZero();
    }

    @Test
    void reImportingTodayChangesNothing() throws Exception {
        String admin = login("admin@mandi.local", "Admin@12345");

        JsonNode run = json.readTree(mvc.perform(post("/api/admin/imports").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(run.get("trigger").asText()).isEqualTo("MANUAL");
        assertThat(run.get("status").asText()).isIn("SUCCESS", "PARTIAL");
        assertThat(run.get("inserted").asInt()).isZero(); // today's rows came in with the startup backfill
        assertThat(run.get("updated").asInt()).isZero();

        JsonNode overview = getJson("/api/admin/imports", admin);
        assertThat(overview.get("source").asText()).isEqualTo("sample");
        assertThat(overview.get("runs").size()).isGreaterThanOrEqualTo(3);
        assertThat(overview.get("runs").get(0).get("trigger").asText()).isEqualTo("MANUAL");
    }
}
