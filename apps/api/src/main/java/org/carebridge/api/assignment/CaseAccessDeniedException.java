package org.carebridge.api.assignment;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * A failed case-level check. Mapped to the standard 403 {@code forbidden} envelope; the reason
 * stays on the server.
 */
public class CaseAccessDeniedException extends AccessDeniedException {

    private static final long serialVersionUID = 1L;

    private final transient CaseAccessDenial reason;
    private final String permission;
    private final transient UUID target;

    public CaseAccessDeniedException(CaseAccessDenial reason, String permission, UUID target) {
        super("case access denied: " + reason);
        this.reason = reason;
        this.permission = permission;
        this.target = target;
    }

    public CaseAccessDenial reason() {
        return reason;
    }

    public String permission() {
        return permission;
    }

    /** The case id, or the care unit id for unit-level checks. */
    public UUID target() {
        return target;
    }
}
