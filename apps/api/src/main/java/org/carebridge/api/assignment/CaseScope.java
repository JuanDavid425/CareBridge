package org.carebridge.api.assignment;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Which cases one account may touch with one permission. Single-case checks use
 * {@link #denialFor(CaseRef)}; repositories turn the same scope into their WHERE clause so a list
 * never contains a case the single-case check would refuse:
 *
 * <ul>
 *   <li>{@link Kind#CARE_TEAM}: {@code care_unit_id = ANY(:careUnitIds)} and the account is on the
 *       case's care team</li>
 *   <li>{@link Kind#OWN_CASE}: the account is linked to the case</li>
 *   <li>{@link Kind#NONE}: no rows; skip the query</li>
 * </ul>
 */
public record CaseScope(Kind kind, UUID accountId, Set<UUID> careUnitIds) {

    public enum Kind {
        CARE_TEAM,
        OWN_CASE,
        NONE
    }

    public CaseScope {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(accountId, "accountId");
        careUnitIds = kind == Kind.CARE_TEAM ? Set.copyOf(careUnitIds) : Set.of();
    }

    public static CaseScope careTeam(UUID accountId, Set<UUID> careUnitIds) {
        return new CaseScope(Kind.CARE_TEAM, accountId, careUnitIds);
    }

    public static CaseScope ownCase(UUID accountId) {
        return new CaseScope(Kind.OWN_CASE, accountId, Set.of());
    }

    public static CaseScope none(UUID accountId) {
        return new CaseScope(Kind.NONE, accountId, Set.of());
    }

    /** True when no case can match, so a repository can return an empty page without querying. */
    public boolean isEmpty() {
        return kind == Kind.NONE || (kind == Kind.CARE_TEAM && careUnitIds.isEmpty());
    }

    /** Empty when the case is in scope, otherwise the first rule it breaks. */
    public Optional<CaseAccessDenial> denialFor(CaseRef target) {
        return switch (kind) {
            case NONE -> Optional.of(CaseAccessDenial.NO_PERMISSION);
            case CARE_TEAM -> !careUnitIds.contains(target.careUnitId())
                    ? Optional.of(CaseAccessDenial.OUTSIDE_UNIT)
                    : target.careTeam().contains(accountId)
                            ? Optional.empty()
                            : Optional.of(CaseAccessDenial.NOT_ASSIGNED);
            case OWN_CASE -> target.portalAccounts().contains(accountId)
                    ? Optional.empty()
                    : Optional.of(CaseAccessDenial.NOT_LINKED);
        };
    }

    /**
     * For actions on a unit before a case exists (creating a case): only care-team scopes with a
     * membership in that unit qualify.
     */
    public Optional<CaseAccessDenial> denialForUnit(UUID careUnitId) {
        return switch (kind) {
            case CARE_TEAM -> careUnitIds.contains(careUnitId)
                    ? Optional.empty()
                    : Optional.of(CaseAccessDenial.OUTSIDE_UNIT);
            case OWN_CASE, NONE -> Optional.of(CaseAccessDenial.NO_PERMISSION);
        };
    }
}
