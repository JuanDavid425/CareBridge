package org.carebridge.api.identity;

import java.io.Serializable;
import java.util.UUID;

/** An active membership of a care-team account in a care unit. */
public record UnitMembership(UUID careUnitId, String unitCode, String unitName, Role role)
        implements Serializable {
}
