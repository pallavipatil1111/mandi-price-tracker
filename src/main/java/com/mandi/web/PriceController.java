package com.mandi.web;

import com.mandi.service.PriceQueryService;
import com.mandi.web.dto.PriceViews;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only price data. */
@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PriceQueryService query;

    public PriceController(PriceQueryService query) {
        this.query = query;
    }

    @GetMapping("/catalog")
    public PriceViews.Catalog catalog() {
        return query.catalog();
    }

    @GetMapping("/board")
    public PriceViews.Board board() {
        return query.board();
    }

    @GetMapping("/series")
    public PriceViews.Series series(@RequestParam Long commodityId, @RequestParam Long marketId,
                                    @RequestParam(defaultValue = "90") int days) {
        return query.series(commodityId, marketId, days);
    }

    @GetMapping("/compare")
    public PriceViews.Compare compare(@RequestParam Long commodityId) {
        return query.compare(commodityId);
    }

    @GetMapping("/monthly")
    public PriceViews.Monthly monthly(@RequestParam Long commodityId, @RequestParam Long marketId) {
        return query.monthly(commodityId, marketId);
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(@RequestParam Long commodityId, @RequestParam Long marketId,
                                         @RequestParam(defaultValue = "365") int days) {
        PriceViews.Series s = query.series(commodityId, marketId, days);
        String name = (s.commodity() + "-" + s.market() + "-" + days + "d").replaceAll("[^A-Za-z0-9-]+", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(query.csv(commodityId, marketId, days).getBytes(StandardCharsets.UTF_8));
    }
}
