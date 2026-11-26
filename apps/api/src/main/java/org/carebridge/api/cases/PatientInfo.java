package org.carebridge.api.cases;

import java.util.UUID;

/** A synthetic patient. Only an invented "(DEMO)" name and an age band; nothing identifying. */
public record PatientInfo(UUID id, String reference, String displayName, String ageBand) {
}
