CREATE TABLE staffing_ledgers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  org_unit_id INTEGER NOT NULL UNIQUE,
  actual_staffing INTEGER NOT NULL DEFAULT 0 CHECK (actual_staffing >= 0),
  leadership_positions_approved INTEGER NOT NULL DEFAULT 0
    CHECK (leadership_positions_approved >= 0),
  leadership_positions_occupied INTEGER NOT NULL DEFAULT 0
    CHECK (leadership_positions_occupied >= 0),
  external_staff INTEGER NOT NULL DEFAULT 0 CHECK (external_staff >= 0),
  data_date TEXT NOT NULL,
  remarks TEXT,
  last_change_summary TEXT,
  version_no INTEGER NOT NULL DEFAULT 1 CHECK (version_no >= 1),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  FOREIGN KEY (org_unit_id) REFERENCES org_units(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_staffing_ledgers_data_date ON staffing_ledgers(data_date);
CREATE INDEX idx_staffing_ledgers_updated_at ON staffing_ledgers(updated_at DESC);

CREATE TABLE staffing_change_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  change_group_no TEXT NOT NULL,
  ledger_id INTEGER NOT NULL,
  org_unit_id INTEGER NOT NULL,
  change_source TEXT NOT NULL CHECK (
    change_source IN ('MANUAL_CREATE','MANUAL_UPDATE','BATCH_UPDATE','EXCEL_IMPORT')
  ),
  approved_staffing_before INTEGER,
  approved_staffing_after INTEGER,
  actual_staffing_before INTEGER,
  actual_staffing_after INTEGER,
  leadership_approved_before INTEGER,
  leadership_approved_after INTEGER,
  leadership_occupied_before INTEGER,
  leadership_occupied_after INTEGER,
  external_staff_before INTEGER,
  external_staff_after INTEGER,
  changed_fields TEXT NOT NULL,
  data_date TEXT NOT NULL,
  change_reason TEXT NOT NULL,
  operator_id INTEGER,
  operated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  FOREIGN KEY (ledger_id) REFERENCES staffing_ledgers(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (org_unit_id) REFERENCES org_units(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (operator_id) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_staffing_change_logs_ledger_time
  ON staffing_change_logs(ledger_id, operated_at DESC);
CREATE INDEX idx_staffing_change_logs_org_time
  ON staffing_change_logs(org_unit_id, operated_at DESC);
CREATE INDEX idx_staffing_change_logs_group
  ON staffing_change_logs(change_group_no);

CREATE TABLE staffing_import_batches (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  batch_no TEXT NOT NULL UNIQUE,
  file_name TEXT NOT NULL,
  file_size INTEGER NOT NULL CHECK (file_size >= 0),
  total_rows INTEGER NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
  success_rows INTEGER NOT NULL DEFAULT 0 CHECK (success_rows >= 0),
  failed_rows INTEGER NOT NULL DEFAULT 0 CHECK (failed_rows >= 0),
  warning_rows INTEGER NOT NULL DEFAULT 0 CHECK (warning_rows >= 0),
  status TEXT NOT NULL CHECK (status IN ('PROCESSING','COMPLETED','PARTIAL','FAILED')),
  imported_by INTEGER,
  imported_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  FOREIGN KEY (imported_by) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_staffing_import_batches_time
  ON staffing_import_batches(imported_at DESC);

CREATE TABLE staffing_import_errors (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  batch_id INTEGER NOT NULL,
  row_number INTEGER NOT NULL CHECK (row_number >= 2),
  org_unit_code TEXT,
  org_unit_name TEXT,
  raw_data TEXT,
  error_message TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  FOREIGN KEY (batch_id) REFERENCES staffing_import_batches(id)
    ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX idx_staffing_import_errors_batch_row
  ON staffing_import_errors(batch_id, row_number);
