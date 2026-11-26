package org.carebridge.api.cases;

import java.util.List;

/** Coarse age bands for synthetic patients; no dates of birth are stored. Mirrors the V4 CHECK. */
public final class AgeBands {

    public static final List<String> ALL = List.of("18-29", "30-39", "40-49", "50-59", "60-69", "70-79", "80+");

    private AgeBands() {
    }

    public static boolean isValid(String band) {
        return band != null && ALL.contains(band);
    }
}
