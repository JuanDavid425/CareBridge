package org.carebridge.api.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import org.carebridge.api.identity.IdentityStore;
import org.carebridge.api.identity.RolePermission;
import org.carebridge.api.security.AuthController.UnitView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Identity", description = "Care units and the role matrix")
public class IdentityController {

    private final IdentityStore identities;

    public IdentityController(IdentityStore identities) {
        this.identities = identities;
    }

    public record RoleMatrixEntry(String role, String permission, String scope) {

        static RoleMatrixEntry of(RolePermission row) {
            return new RoleMatrixEntry(row.role().code(), row.permission(), row.scope().name().toLowerCase(Locale.ROOT));
        }
    }

    /** Coordinators and reviewers only (SecurityConfig); feeds the unit switcher. */
    @GetMapping("/me/units")
    @Operation(summary = "Active care units of the signed-in care-team member")
    public List<UnitView> myUnits(@AuthenticationPrincipal CareBridgeUser user) {
        return identities.findById(user.id())
                .map(account -> account.memberships().stream().map(UnitView::of).toList())
                .orElse(List.of());
    }

    /** Administrators only (SecurityConfig). */
    @GetMapping("/admin/role-matrix")
    @Operation(summary = "Every role's permissions and their scope")
    public List<RoleMatrixEntry> roleMatrix() {
        return identities.roleMatrix().stream().map(RoleMatrixEntry::of).toList();
    }
}
