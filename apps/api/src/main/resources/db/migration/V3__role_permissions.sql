-- Role matrix: which permission each role holds and the scope it applies to.
--   unit      only for cases in care units where the user has an active membership
--   own_case  only for the case the portal account is linked to
--   global    instance-wide (administration, never case content)
--
-- This is reference data, not demo data, so it ships with the schema. The UI and the
-- permission tests read it from here instead of hardcoding role names.

CREATE TABLE role_permissions (
    role        text NOT NULL,
    permission  text NOT NULL,
    scope       text NOT NULL,
    PRIMARY KEY (role, permission),
    CONSTRAINT role_permissions_role_check
        CHECK (role IN ('coordinator', 'reviewer', 'patient', 'caregiver', 'admin')),
    CONSTRAINT role_permissions_permission_format CHECK (permission ~ '^[a-z]+(\.[a-z_]+)+$'),
    CONSTRAINT role_permissions_scope_check CHECK (scope IN ('unit', 'own_case', 'global'))
);

INSERT INTO role_permissions (role, permission, scope) VALUES
    ('coordinator', 'case.read',            'unit'),
    ('coordinator', 'case.create',          'unit'),
    ('coordinator', 'document.upload',      'unit'),
    ('coordinator', 'task.edit',            'unit'),
    ('coordinator', 'task.assign',          'unit'),
    ('coordinator', 'audit.read',           'unit'),
    ('reviewer',    'case.read',            'unit'),
    ('reviewer',    'proposal.review',      'unit'),
    ('reviewer',    'plan.publish',         'unit'),
    ('reviewer',    'audit.read',           'unit'),
    ('patient',     'plan.read_published',  'own_case'),
    ('caregiver',   'plan.read_published',  'own_case'),
    ('admin',       'user.manage',          'global'),
    ('admin',       'unit.manage',          'global'),
    ('admin',       'audit.read',           'global');
