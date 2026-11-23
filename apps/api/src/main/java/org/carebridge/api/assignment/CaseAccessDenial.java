package org.carebridge.api.assignment;

/**
 * Why a case-level check failed. Logged with the request id for support; never sent to the
 * client, which always gets the same {@code forbidden} envelope.
 */
public enum CaseAccessDenial {
    /** The role has no such permission, or only a global one (global never covers case content). */
    NO_PERMISSION,
    /** Care-team member without an active membership in the case's care unit. */
    OUTSIDE_UNIT,
    /** Member of the case's unit but not on the case's care team. */
    NOT_ASSIGNED,
    /** Patient or caregiver account that is not linked to the case. */
    NOT_LINKED
}
