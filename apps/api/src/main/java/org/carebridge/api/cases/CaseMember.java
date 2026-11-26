package org.carebridge.api.cases;

import java.util.UUID;
import org.carebridge.api.identity.Role;

/** A staff account on a case's care team (or eligible to join one). */
public record CaseMember(UUID userId, String displayName, Role role) {
}
