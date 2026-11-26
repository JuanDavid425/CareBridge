-- Case registry: synthetic patients, discharge cases, care teams, portal links and case events.
--
-- Everything here is invented data. Patients and cases carry a synthetic marker that cannot be
-- false, DEMO- references and "(DEMO)" display names, so a row can never be mistaken for a real
-- record. Status transitions are enforced by the API (CaseStatus); the database keeps the values,
-- versions and relationships consistent.

CREATE SEQUENCE patient_reference_seq START WITH 1001;
CREATE SEQUENCE case_reference_seq START WITH 1001;

CREATE TABLE patients (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    reference         text        NOT NULL,
    display_name      text        NOT NULL,
    age_band          text        NOT NULL,
    synthetic_marker  boolean     NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT patients_reference_key UNIQUE (reference),
    CONSTRAINT patients_reference_format CHECK (reference ~ '^DEMO-PT-[0-9]{3,9}$'),
    CONSTRAINT patients_synthetic_only CHECK (synthetic_marker),
    CONSTRAINT patients_display_name_demo
        CHECK (display_name ~ '^\S.* \(DEMO\)$' AND length(display_name) <= 120),
    CONSTRAINT patients_age_band_check
        CHECK (age_band IN ('18-29', '30-39', '40-49', '50-59', '60-69', '70-79', '80+'))
);

CREATE TABLE discharge_cases (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    reference         text        NOT NULL,
    care_unit_id      uuid        NOT NULL REFERENCES care_units (id),
    patient_id        uuid        NOT NULL REFERENCES patients (id),
    status            text        NOT NULL DEFAULT 'open',
    discharge_date    date        NOT NULL,
    -- Date the post-discharge follow-up is due; drives the "due" filter and overdue flag.
    follow_up_due     date        NOT NULL,
    summary           text        NOT NULL DEFAULT '',
    synthetic_marker  boolean     NOT NULL DEFAULT true,
    -- Optimistic lock: every change increments it and is sent as the ETag ("v3").
    version           integer     NOT NULL DEFAULT 1,
    created_by        uuid        REFERENCES users (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    closed_at         timestamptz,
    CONSTRAINT discharge_cases_reference_key UNIQUE (reference),
    CONSTRAINT discharge_cases_reference_format CHECK (reference ~ '^DEMO-CASE-[0-9]{3,9}$'),
    CONSTRAINT discharge_cases_status_check
        CHECK (status IN ('open', 'planning', 'review', 'active', 'closed')),
    CONSTRAINT discharge_cases_synthetic_only CHECK (synthetic_marker),
    CONSTRAINT discharge_cases_version_positive CHECK (version >= 1),
    CONSTRAINT discharge_cases_follow_up_after_discharge CHECK (follow_up_due >= discharge_date),
    CONSTRAINT discharge_cases_follow_up_window CHECK (follow_up_due <= discharge_date + 90),
    CONSTRAINT discharge_cases_summary_length CHECK (length(summary) <= 2000),
    CONSTRAINT discharge_cases_closed_at_matches_status CHECK ((status = 'closed') = (closed_at IS NOT NULL)),
    -- Target of the composite foreign key from case_assignments.
    CONSTRAINT discharge_cases_id_unit_key UNIQUE (id, care_unit_id)
);

-- A synthetic patient has at most one case that is not closed.
CREATE UNIQUE INDEX discharge_cases_one_open_per_patient
    ON discharge_cases (patient_id) WHERE status <> 'closed';
-- Case lists: unit scope first, then status and due-date filters.
CREATE INDEX discharge_cases_unit_status_due_idx
    ON discharge_cases (care_unit_id, status, follow_up_due);

-- Care team of a case. An assignee must be an (active or former) member of the case's unit with
-- the same role; CaseAuthorizationService also requires the membership to be active right now.
CREATE TABLE case_assignments (
    case_id       uuid        NOT NULL,
    user_id       uuid        NOT NULL,
    care_unit_id  uuid        NOT NULL,
    role          text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (case_id, user_id),
    CONSTRAINT case_assignments_role_check CHECK (role IN ('coordinator', 'reviewer')),
    CONSTRAINT case_assignments_case_unit_fkey
        FOREIGN KEY (case_id, care_unit_id) REFERENCES discharge_cases (id, care_unit_id),
    CONSTRAINT case_assignments_membership_fkey
        FOREIGN KEY (user_id, care_unit_id) REFERENCES team_memberships (user_id, care_unit_id),
    CONSTRAINT case_assignments_user_role_fkey
        FOREIGN KEY (user_id, role) REFERENCES users (id, role)
);

CREATE INDEX case_assignments_user_idx ON case_assignments (user_id, case_id);

-- Patient and caregiver demo accounts linked to a case (own_case scope).
CREATE TABLE case_portal_links (
    case_id     uuid        NOT NULL REFERENCES discharge_cases (id),
    user_id     uuid        NOT NULL,
    role        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (case_id, user_id),
    CONSTRAINT case_portal_links_role_check CHECK (role IN ('patient', 'caregiver')),
    CONSTRAINT case_portal_links_user_role_fkey
        FOREIGN KEY (user_id, role) REFERENCES users (id, role)
);

-- Append-only history of each case. Field names only, never values, so the trail holds no
-- patient content.
CREATE TABLE case_events (
    id              bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id         uuid        NOT NULL REFERENCES discharge_cases (id),
    -- Null for changes made by the demo seed.
    actor_id        uuid        REFERENCES users (id),
    type            text        NOT NULL,
    from_status     text,
    to_status       text,
    changed_fields  text[]      NOT NULL DEFAULT '{}',
    version         integer     NOT NULL,
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT case_events_type_check CHECK (type IN ('created', 'updated', 'status_changed', 'seeded')),
    CONSTRAINT case_events_status_change_check
        CHECK ((type = 'status_changed') = (from_status IS NOT NULL AND to_status IS NOT NULL))
);

CREATE INDEX case_events_case_idx ON case_events (case_id, id);

CREATE FUNCTION case_events_append_only() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'case_events is append-only' USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER case_events_no_update_or_delete
    BEFORE UPDATE OR DELETE ON case_events
    FOR EACH ROW EXECUTE FUNCTION case_events_append_only();

-- Coordinators edit case details and move cases through intake and planning.
INSERT INTO role_permissions (role, permission, scope) VALUES
    ('coordinator', 'case.update', 'unit');
