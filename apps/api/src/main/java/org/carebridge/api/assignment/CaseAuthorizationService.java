package org.carebridge.api.assignment;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.carebridge.api.identity.Account;
import org.carebridge.api.identity.IdentityStore;
import org.carebridge.api.identity.RolePermission;
import org.carebridge.api.identity.UnitMembership;
import org.carebridge.api.security.CareBridgeUser;
import org.springframework.stereotype.Service;

/**
 * Case-assignment policy: the server-side answer to "may this account do X to this case".
 *
 * <ol>
 *   <li>The account's role must hold the permission in the role matrix ({@code role_permissions}).</li>
 *   <li>{@code unit} scope: an active membership in the case's care unit <em>and</em> a place on the
 *       case's care team. Unit membership alone is not enough.</li>
 *   <li>{@code own_case} scope: the portal account must be linked to the case.</li>
 *   <li>{@code global} scope never grants case content; administrators manage the instance only.</li>
 * </ol>
 *
 * Memberships and the active flag are read fresh on every check, so removing someone from a unit
 * or deactivating them takes effect on their next request.
 */
@Service
public class CaseAuthorizationService {

    private final IdentityStore identities;

    public CaseAuthorizationService(IdentityStore identities) {
        this.identities = identities;
    }

    /** The cases {@code accountId} may touch with {@code permission}; feeds repository queries. */
    public CaseScope scopeFor(UUID accountId, String permission) {
        Optional<Account> found = identities.findById(accountId).filter(Account::active);
        if (found.isEmpty()) {
            return CaseScope.none(accountId);
        }
        Account account = found.get();
        Optional<RolePermission.Scope> scope = identities.roleMatrix().stream()
                .filter(row -> row.role() == account.role() && row.permission().equals(permission))
                .map(RolePermission::scope)
                .findFirst();
        if (scope.isEmpty()) {
            return CaseScope.none(accountId);
        }
        return switch (scope.get()) {
            case UNIT -> CaseScope.careTeam(accountId, account.memberships().stream()
                    .map(UnitMembership::careUnitId)
                    .collect(Collectors.toSet()));
            case OWN_CASE -> CaseScope.ownCase(accountId);
            case GLOBAL -> CaseScope.none(accountId);
        };
    }

    /** Empty when allowed, otherwise the reason it is not. */
    public Optional<CaseAccessDenial> check(UUID accountId, CaseRef target, String permission) {
        return scopeFor(accountId, permission).denialFor(target);
    }

    /** Throws {@link CaseAccessDeniedException} (403) unless the user may act on the case. */
    public void require(CareBridgeUser user, CaseRef target, String permission) {
        check(user.id(), target, permission).ifPresent(reason -> {
            throw new CaseAccessDeniedException(reason, permission, target.caseId());
        });
    }

    /** For unit-level actions such as creating a case in {@code careUnitId}. */
    public void requireInUnit(CareBridgeUser user, UUID careUnitId, String permission) {
        scopeFor(user.id(), permission).denialForUnit(careUnitId).ifPresent(reason -> {
            throw new CaseAccessDeniedException(reason, permission, careUnitId);
        });
    }
}
