package com.mandi.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandi.core.RawRecord;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataGovInSourceTest {

    private static DataGovInSource source(String baseUrl, String key) {
        return new DataGovInSource(new DataGovInSource.Settings(baseUrl, "9ef84268-d588-465a-a308-a864a43d0070", key,
                "Maharashtra", "state.keyword", 2, 10), HttpClient.newHttpClient(), new ObjectMapper(), Duration.ofMillis(1));
    }

    @Test
    void buildsAPagedUrlWithAnEncodedStateFilter() {
        String uri = source("https://api.data.gov.in", "my key").pageUri(500).toString();
        assertThat(uri).startsWith("https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070?");
        assertThat(uri).contains("api-key=my+key");
        assertThat(uri).contains("&format=json&limit=2&offset=500");
        assertThat(uri).contains("&filters%5Bstate.keyword%5D=Maharashtra");
    }

    @Test
    void refusesToRunWithoutAnApiKey() {
        assertThatThrownBy(() -> source("https://api.data.gov.in", " ").fetch(LocalDate.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATA_GOV_API_KEY");
    }

    /** A local HTTP server plays data.gov.in: two pages of results, with one 503 to retry through. */
    @Test
    void pagesThroughResultsAndRetriesServerErrors() throws Exception {
        String row = """
                {"state":"Maharashtra","district":"Nashik","market":"Lasalgaon","commodity":"Onion","variety":"Red",
                 "grade":"FAQ","arrival_date":"13/10/2026","min_price":"1800","max_price":"2450","modal_price":"%s"}""";
        List<String> pages = List.of(
                "{\"total\":3,\"count\":2,\"records\":[" + row.formatted("2150") + "," + row.formatted("2160") + "]}",
                "{\"total\":3,\"count\":1,\"records\":[" + row.formatted("2170") + "]}");
        List<String> queries = new CopyOnWriteArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            int n = calls.getAndIncrement();
            byte[] body;
            int status;
            if (n == 1) {
                status = 503; // transient failure on the second page
                body = "busy".getBytes(StandardCharsets.UTF_8);
            } else {
                status = 200;
                body = pages.get(n == 0 ? 0 : 1).getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            List<RawRecord> rows = source("http://127.0.0.1:" + server.getAddress().getPort(), "k").fetch(LocalDate.now());
            assertThat(rows).extracting(RawRecord::modalPrice).containsExactly("2150", "2160", "2170");
            assertThat(rows.get(0).arrivalDate()).isEqualTo("13/10/2026");
            assertThat(calls.get()).isEqualTo(3);
            assertThat(queries.get(2)).contains("offset=2");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotRetryAWrongKey() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        try {
            assertThatThrownBy(() -> source("http://127.0.0.1:" + server.getAddress().getPort(), "bad").fetch(LocalDate.now()))
                    .hasMessageContaining("HTTP 403");
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }
}
