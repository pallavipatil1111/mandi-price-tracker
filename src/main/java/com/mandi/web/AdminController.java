package com.mandi.web;

import com.mandi.domain.ImportTrigger;
import com.mandi.ingest.ImportService;
import com.mandi.repository.ImportRunRepository;
import com.mandi.web.dto.ImportRunView;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Import monitoring for ROLE_ADMIN (enforced in SecurityConfig for /api/admin/**). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record ImportOverview(String source, String schedule, String zone, boolean scheduleEnabled,
                                 boolean running, List<ImportRunView> runs) {
    }

    private final ImportService imports;
    private final ImportRunRepository runs;
    private final String cron;
    private final String zone;
    private final boolean enabled;

    public AdminController(ImportService imports, ImportRunRepository runs, @Value("${app.import.cron}") String cron,
                           @Value("${app.zone}") String zone, @Value("${app.import.enabled:true}") boolean enabled) {
        this.imports = imports;
        this.runs = runs;
        this.cron = cron;
        this.zone = zone;
        this.enabled = enabled;
    }

    @GetMapping("/imports")
    public ImportOverview imports() {
        return new ImportOverview(imports.sourceName(), cron, zone, enabled, imports.isRunning(),
                runs.findTop30ByOrderByStartedAtDescIdDesc().stream().map(ImportRunView::of).toList());
    }

    @PostMapping("/imports")
    public ImportRunView importNow() {
        return ImportRunView.of(imports.importToday(ImportTrigger.MANUAL));
    }
}
