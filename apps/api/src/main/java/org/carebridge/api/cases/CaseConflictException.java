package org.carebridge.api.cases;

/**
 * A request that is well formed but not allowed in the case's current state, such as an illegal
 * status transition. Mapped to 409 with a stable code.
 */
public class CaseConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String INVALID_TRANSITION = "invalid_transition";
    public static final String CASE_CLOSED = "case_closed";

    private final String code;

    public CaseConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
