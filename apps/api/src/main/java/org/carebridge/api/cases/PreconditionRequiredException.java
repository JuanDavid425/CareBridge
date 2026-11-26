package org.carebridge.api.cases;

/** A write without a usable If-Match header. Mapped to 428 {@code precondition_required}. */
public class PreconditionRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PreconditionRequiredException() {
        super("If-Match header required");
    }
}
