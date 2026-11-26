package org.carebridge.api.web;

import java.util.Map;

/** Query or path parameters that cannot be used. Mapped to 400 {@code invalid_parameter}. */
public class InvalidParameterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fields;

    public InvalidParameterException(Map<String, String> fields) {
        super("invalid request parameters");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}
