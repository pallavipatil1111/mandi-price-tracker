package com.mandi.config;

import com.mandi.web.dto.UserViews;
import java.util.List;

/** Exposes StartupData's package-private helpers to tests in other packages. */
public final class StartupDataAccess {

    private StartupDataAccess() {
    }

    public static UserViews.AlertRequest demoAlert(int start, List<Integer> ahead, boolean waiting) {
        return StartupData.demoAlert(1L, 2L, start, ahead, waiting);
    }
}
