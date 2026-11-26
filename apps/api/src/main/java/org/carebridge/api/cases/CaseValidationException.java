package org.carebridge.api.cases;

import java.util.Map;

/** Field problems found by the service (cross-field rules). Mapped to 422 {@code validation_failed}. */
public class CaseValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fields;

    public CaseValidationException(Map<String, String> fields) {
        super("case request is invalid");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}
