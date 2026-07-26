CREATE TABLE evaluation_archive_number_sequences (
  evaluation_year INTEGER PRIMARY KEY,
  last_value INTEGER NOT NULL DEFAULT 0 CHECK (last_value >= 0)
);

CREATE TABLE evaluation_archives (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  archive_no TEXT NOT NULL UNIQUE,
  org_unit_id INTEGER NOT NULL,
  evaluation_year INTEGER NOT NULL CHECK (evaluation_year BETWEEN 1900 AND 2999),
  evaluation_type TEXT NOT NULL CHECK (
    evaluation_type IN ('ANNUAL_COMPREHENSIVE', 'SPECIAL', 'AD_HOC')
  ),
  evaluation_grade TEXT NOT NULL DEFAULT 'UNRATED' CHECK (
    evaluation_grade IN ('EXCELLENT', 'GOOD', 'QUALIFIED', 'UNQUALIFIED', 'UNRATED')
  ),
  description TEXT,
  access_level TEXT NOT NULL DEFAULT 'DEPARTMENT' CHECK (
    access_level IN ('PUBLIC', 'DEPARTMENT', 'AUTHORIZED')
  ),
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ARCHIVED')),
  row_version INTEGER NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  archived_by INTEGER,
  archived_at TEXT,
  FOREIGN KEY (org_unit_id) REFERENCES org_units(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (archived_by) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  UNIQUE (org_unit_id, evaluation_year, evaluation_type)
);

CREATE INDEX idx_evaluation_archives_org_year
  ON evaluation_archives(org_unit_id, evaluation_year);
CREATE INDEX idx_evaluation_archives_filters
  ON evaluation_archives(evaluation_year, evaluation_type, evaluation_grade, status);
CREATE INDEX idx_evaluation_archives_updated
  ON evaluation_archives(updated_at DESC);

CREATE TABLE evaluation_archive_attachments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  archive_id INTEGER NOT NULL,
  attachment_id INTEGER NOT NULL UNIQUE,
  category TEXT NOT NULL CHECK (
    category IN ('REPORT', 'SELF_ASSESSMENT', 'RECTIFICATION_LEDGER', 'REVIEW_RECORD', 'OTHER')
  ),
  version_group TEXT NOT NULL,
  version_no INTEGER NOT NULL DEFAULT 1 CHECK (version_no >= 1),
  is_current INTEGER NOT NULL DEFAULT 1 CHECK (is_current IN (0, 1)),
  previous_relation_id INTEGER,
  remarks TEXT,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  FOREIGN KEY (archive_id) REFERENCES evaluation_archives(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (attachment_id) REFERENCES sys_attachments(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (previous_relation_id) REFERENCES evaluation_archive_attachments(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  UNIQUE (version_group, version_no)
);

CREATE UNIQUE INDEX uq_evaluation_archive_attachment_current
  ON evaluation_archive_attachments(version_group) WHERE is_current = 1;
CREATE INDEX idx_evaluation_archive_attachments_archive
  ON evaluation_archive_attachments(archive_id, is_current, category);
CREATE INDEX idx_evaluation_archive_attachments_previous
  ON evaluation_archive_attachments(previous_relation_id);
