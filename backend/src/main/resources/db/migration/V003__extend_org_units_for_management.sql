ALTER TABLE org_units ADD COLUMN approved_staffing INTEGER
  CHECK (approved_staffing IS NULL OR approved_staffing >= 0);
ALTER TABLE org_units ADD COLUMN verification_status TEXT NOT NULL DEFAULT 'PENDING'
  CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'));
ALTER TABLE org_units ADD COLUMN verification_opinion TEXT;
ALTER TABLE org_units ADD COLUMN created_by INTEGER
  REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL;
ALTER TABLE org_units ADD COLUMN updated_by INTEGER
  REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL;
ALTER TABLE org_units ADD COLUMN verified_by INTEGER
  REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL;
ALTER TABLE org_units ADD COLUMN verified_at TEXT;
ALTER TABLE org_units ADD COLUMN version_no INTEGER NOT NULL DEFAULT 1
  CHECK (version_no >= 1);

UPDATE org_units
SET created_by = (SELECT id FROM sys_users WHERE username = 'zhang.zhuren'),
    updated_by = (SELECT id FROM sys_users WHERE username = 'zhang.zhuren')
WHERE created_by IS NULL OR updated_by IS NULL;

CREATE UNIQUE INDEX idx_org_units_code_nocase
  ON org_units(unit_code COLLATE NOCASE);
CREATE INDEX idx_org_units_verification_status
  ON org_units(verification_status);

CREATE TABLE org_unit_verifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  org_unit_id INTEGER NOT NULL,
  verification_result TEXT NOT NULL
    CHECK (verification_result IN ('VERIFIED', 'REJECTED')),
  verification_opinion TEXT,
  verifier_id INTEGER,
  verified_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  created_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  FOREIGN KEY (org_unit_id) REFERENCES org_units(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (verifier_id) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_org_unit_verifications_unit_time
  ON org_unit_verifications(org_unit_id, verified_at DESC);
CREATE INDEX idx_org_unit_verifications_verifier_time
  ON org_unit_verifications(verifier_id, verified_at DESC);
