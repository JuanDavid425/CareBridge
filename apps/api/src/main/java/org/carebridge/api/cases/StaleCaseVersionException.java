package org.carebridge.api.cases;

/** The case changed since the caller read it (If-Match mismatch). Mapped to 412 {@code stale_version}. */
public class StaleCaseVersionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int currentVersion;

    public StaleCaseVersionException(int currentVersion) {
        super("case version is stale");
        this.currentVersion = currentVersion;
    }

    public int currentVersion() {
        return currentVersion;
    }
}
