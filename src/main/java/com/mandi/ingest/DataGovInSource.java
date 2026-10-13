package com.mandi.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandi.core.RawRecord;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live prices from the Government of India Open Data API: "Current Daily Price of Various Commodities
 * from Various Markets (Mandi)", published from Agmarknet. Pages through the result set, filters to one
 * state, and retries transient failures (timeouts, 429 and 5xx) with exponential backoff.
 */
public class DataGovInSource implements PriceSource {

    private static final Logger log = LoggerFactory.getLogger(DataGovInSource.class);
    static final int MAX_ATTEMPTS = 3;

    public record Settings(String baseUrl, String resourceId, String apiKey, String state, String stateFilterField,
                           int pageSize, int maxPages) {
    }

    private final Settings settings;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Duration backoff;

    public DataGovInSource(Settings settings, HttpClient http, ObjectMapper json, Duration backoff) {
        this.settings = settings;
        this.http = http;
        this.json = json;
        this.backoff = backoff;
    }

    @Override
    public String name() {
        return "agmarknet";
    }

    @Override
    public List<RawRecord> fetch(LocalDate ignored) throws IOException, InterruptedException {
        if (settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new IllegalStateException("DATA_GOV_API_KEY is not set - get a free key at data.gov.in");
        }
        List<RawRecord> rows = new ArrayList<>();
        int offset = 0;
        for (int page = 0; page < settings.maxPages(); page++) {
            JsonNode body = json.readTree(getWithRetry(pageUri(offset)));
            JsonNode records = body.path("records");
            for (JsonNode r : records) {
                rows.add(new RawRecord(text(r, "state"), text(r, "district"), text(r, "market"), text(r, "commodity"),
                        text(r, "variety"), text(r, "arrival_date"), text(r, "min_price"), text(r, "max_price"),
                        text(r, "modal_price")));
            }
            offset += records.size();
            long total = body.path("total").asLong(0);
            if (records.size() == 0 || offset >= total) {
                return rows;
            }
        }
        log.warn("Stopped after {} pages ({} rows); raise DATA_GOV_MAX_PAGES to fetch more", settings.maxPages(), rows.size());
        return rows;
    }

    URI pageUri(int offset) {
        StringBuilder url = new StringBuilder(settings.baseUrl()).append("/resource/").append(settings.resourceId())
                .append("?api-key=").append(enc(settings.apiKey()))
                .append("&format=json&limit=").append(settings.pageSize())
                .append("&offset=").append(offset);
        if (settings.state() != null && !settings.state().isBlank()) {
            url.append('&').append(enc("filters[" + settings.stateFilterField() + "]")).append('=').append(enc(settings.state()));
        }
        return URI.create(url.toString());
    }

    private String getWithRetry(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").GET().build();
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return response.body();
                }
                if (status != 429 && status < 500) {
                    // 4xx other than rate limiting will not get better by retrying (bad key, bad resource id)
                    throw new IOException("data.gov.in returned HTTP " + status);
                }
                last = new IOException("data.gov.in returned HTTP " + status);
            } catch (java.net.http.HttpTimeoutException | java.net.ConnectException e) {
                last = e;
            }
            if (attempt < MAX_ATTEMPTS) {
                Thread.sleep(backoff.toMillis() << (attempt - 1));
            }
        }
        throw last;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
