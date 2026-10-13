package com.mandi.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SourceConfig {

    @Bean
    public PriceSource priceSource(@Value("${app.source:sample}") String source,
                                   @Value("${app.datagov.base-url}") String baseUrl,
                                   @Value("${app.datagov.resource-id}") String resourceId,
                                   @Value("${app.datagov.api-key:}") String apiKey,
                                   @Value("${app.datagov.state:}") String state,
                                   @Value("${app.datagov.state-filter-field:state.keyword}") String stateField,
                                   @Value("${app.datagov.page-size:500}") int pageSize,
                                   @Value("${app.datagov.max-pages:40}") int maxPages,
                                   ObjectMapper objectMapper) {
        return switch (source) {
            case "sample" -> new SampleSource();
            case "datagov" -> new DataGovInSource(
                    new DataGovInSource.Settings(baseUrl, resourceId, apiKey, state, stateField, pageSize, maxPages),
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper,
                    Duration.ofSeconds(2));
            default -> throw new IllegalStateException("app.source must be 'sample' or 'datagov', not '" + source + "'");
        };
    }
}
