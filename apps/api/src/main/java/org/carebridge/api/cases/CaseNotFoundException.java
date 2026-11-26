package org.carebridge.api.cases;

/** No case with that id. Mapped to 404 {@code not_found}. */
public class CaseNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CaseNotFoundException() {
        super("case not found");
    }
}
