CREATE TABLE internal_evaluation_tasks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  task_code TEXT NOT NULL COLLATE NOCASE UNIQUE,
  task_name TEXT NOT NULL,
  evaluation_year INTEGER NOT NULL CHECK (evaluation_year BETWEEN 1900 AND 2999),
  task_type TEXT NOT NULL CHECK (task_type IN ('SPECIAL','ANNUAL','POST_ADJUSTMENT')),
  start_date TEXT,
  end_date TEXT,
  description TEXT,
  indicator_version_id INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','PUBLISHED','SCORING','REVIEWING','COMPLETED','CANCELLED')),
  source_task_id INTEGER,
  row_version INTEGER NOT NULL DEFAULT 0,
  published_by INTEGER,
  published_at TEXT,
  completed_by INTEGER,
  completed_at TEXT,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),
  FOREIGN KEY(indicator_version_id) REFERENCES indicator_versions(id) ON DELETE RESTRICT,
  FOREIGN KEY(source_task_id) REFERENCES internal_evaluation_tasks(id) ON DELETE SET NULL,
  FOREIGN KEY(published_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(completed_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(updated_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_internal_tasks_year_status
  ON internal_evaluation_tasks(evaluation_year DESC,status,task_type);

CREATE TABLE internal_evaluation_task_orgs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  task_id INTEGER NOT NULL,
  org_unit_id INTEGER NOT NULL,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE(task_id,org_unit_id),
  FOREIGN KEY(task_id) REFERENCES internal_evaluation_tasks(id) ON DELETE RESTRICT,
  FOREIGN KEY(org_unit_id) REFERENCES org_units(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL
);

CREATE TABLE internal_evaluation_assignments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  task_org_id INTEGER NOT NULL UNIQUE,
  evaluator_id INTEGER NOT NULL,
  reviewer_id INTEGER NOT NULL,
  assigned_by INTEGER,
  assigned_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(task_org_id) REFERENCES internal_evaluation_task_orgs(id) ON DELETE RESTRICT,
  FOREIGN KEY(evaluator_id) REFERENCES sys_users(id) ON DELETE RESTRICT,
  FOREIGN KEY(reviewer_id) REFERENCES sys_users(id) ON DELETE RESTRICT,
  FOREIGN KEY(assigned_by) REFERENCES sys_users(id) ON DELETE SET NULL
);

CREATE TABLE internal_evaluation_indicator_snapshots (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  task_id INTEGER NOT NULL,
  source_indicator_id INTEGER,
  indicator_code TEXT NOT NULL,
  indicator_name TEXT NOT NULL,
  standard_score REAL NOT NULL CHECK (standard_score >= 0),
  weight REAL NOT NULL CHECK (weight BETWEEN 0 AND 100),
  evaluation_method TEXT,
  rules_json TEXT NOT NULL DEFAULT '[]',
  sort_order INTEGER NOT NULL DEFAULT 0,
  UNIQUE(task_id,indicator_code),
  FOREIGN KEY(task_id) REFERENCES internal_evaluation_tasks(id) ON DELETE RESTRICT
);
CREATE INDEX idx_internal_snapshots_task
  ON internal_evaluation_indicator_snapshots(task_id,sort_order,id);

CREATE TABLE internal_evaluation_score_sheets (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  task_org_id INTEGER NOT NULL UNIQUE,
  status TEXT NOT NULL DEFAULT 'NOT_STARTED'
    CHECK (status IN ('NOT_STARTED','DRAFT','SUBMITTED','RETURNED','CONFIRMED')),
  total_score REAL NOT NULL DEFAULT 0 CHECK (total_score >= 0),
  row_version INTEGER NOT NULL DEFAULT 0,
  submitted_by INTEGER,
  submitted_at TEXT,
  confirmed_by INTEGER,
  confirmed_at TEXT,
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(task_org_id) REFERENCES internal_evaluation_task_orgs(id) ON DELETE RESTRICT,
  FOREIGN KEY(submitted_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(confirmed_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(updated_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_internal_score_sheets_status
  ON internal_evaluation_score_sheets(status,updated_at DESC);

CREATE TABLE internal_evaluation_score_entries (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  score_sheet_id INTEGER NOT NULL,
  snapshot_id INTEGER NOT NULL,
  score REAL NOT NULL DEFAULT 0 CHECK (score >= 0),
  basis_type TEXT NOT NULL DEFAULT 'NONE'
    CHECK (basis_type IN ('NONE','DEDUCTION','BONUS','VETO')),
  score_basis TEXT,
  remarks TEXT,
  veto_triggered INTEGER NOT NULL DEFAULT 0 CHECK (veto_triggered IN (0,1)),
  row_version INTEGER NOT NULL DEFAULT 0,
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE(score_sheet_id,snapshot_id),
  FOREIGN KEY(score_sheet_id) REFERENCES internal_evaluation_score_sheets(id) ON DELETE RESTRICT,
  FOREIGN KEY(snapshot_id) REFERENCES internal_evaluation_indicator_snapshots(id) ON DELETE RESTRICT,
  FOREIGN KEY(updated_by) REFERENCES sys_users(id) ON DELETE SET NULL
);

CREATE TABLE internal_evaluation_score_materials (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  score_entry_id INTEGER NOT NULL,
  attachment_id INTEGER NOT NULL UNIQUE,
  material_type TEXT NOT NULL DEFAULT 'EVIDENCE',
  remarks TEXT,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(score_entry_id) REFERENCES internal_evaluation_score_entries(id) ON DELETE RESTRICT,
  FOREIGN KEY(attachment_id) REFERENCES sys_attachments(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_internal_materials_entry
  ON internal_evaluation_score_materials(score_entry_id,created_at DESC);

CREATE TABLE internal_evaluation_reviews (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  score_sheet_id INTEGER NOT NULL,
  review_action TEXT NOT NULL CHECK (review_action IN ('SUBMIT','RETURN','CONFIRM')),
  review_opinion TEXT,
  reviewer_id INTEGER,
  reviewed_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(score_sheet_id) REFERENCES internal_evaluation_score_sheets(id) ON DELETE RESTRICT,
  FOREIGN KEY(reviewer_id) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_internal_reviews_sheet
  ON internal_evaluation_reviews(score_sheet_id,reviewed_at DESC);

CREATE TABLE internal_evaluation_status_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  entity_type TEXT NOT NULL CHECK (entity_type IN ('TASK','SCORE_SHEET')),
  entity_id INTEGER NOT NULL,
  from_status TEXT,
  to_status TEXT NOT NULL,
  reason TEXT,
  operator_id INTEGER,
  changed_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(operator_id) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_internal_status_history_entity
  ON internal_evaluation_status_history(entity_type,entity_id,changed_at DESC);
