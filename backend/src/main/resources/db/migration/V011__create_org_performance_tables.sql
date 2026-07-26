CREATE TABLE org_performance_import_batches (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  batch_code TEXT NOT NULL UNIQUE,
  original_file_name TEXT NOT NULL,
  file_size INTEGER NOT NULL CHECK(file_size>=0),
  total_rows INTEGER NOT NULL DEFAULT 0,
  success_rows INTEGER NOT NULL DEFAULT 0,
  failed_rows INTEGER NOT NULL DEFAULT 0,
  warning_rows INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'PROCESSING'
    CHECK(status IN('PROCESSING','COMPLETED','PARTIAL_FAILED','FAILED')),
  imported_by INTEGER,
  imported_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(imported_by) REFERENCES sys_users(id) ON DELETE SET NULL
);

CREATE TABLE org_performance_field_mappings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source_field TEXT NOT NULL COLLATE NOCASE UNIQUE,
  target_field TEXT NOT NULL CHECK(target_field IN(
    'ORG_CODE','ORG_NAME','YEAR','PERFORMANCE_GRADE',
    'KEY_WORK_SCORE','LEADERSHIP_RATING','REMARKS')),
  required INTEGER NOT NULL DEFAULT 0 CHECK(required IN(0,1)),
  sort_order INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE')),
  row_version INTEGER NOT NULL DEFAULT 0,
  created_by INTEGER,
  updated_by INTEGER,
  created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(updated_by) REFERENCES sys_users(id) ON DELETE SET NULL
);

INSERT INTO org_performance_field_mappings(source_field,target_field,required,sort_order)
VALUES
 ('机构编码','ORG_CODE',1,10),('机构名称','ORG_NAME',0,20),
 ('年度','YEAR',1,30),('绩效等次','PERFORMANCE_GRADE',1,40),
 ('重点工作得分','KEY_WORK_SCORE',0,50),('班子评价','LEADERSHIP_RATING',0,60),
 ('备注','REMARKS',0,70);

CREATE TABLE org_performance_raw_records (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  batch_id INTEGER NOT NULL,
  source_row_number INTEGER NOT NULL,
  org_unit_id INTEGER NOT NULL,
  org_code TEXT NOT NULL,
  source_org_name TEXT,
  evaluation_year INTEGER NOT NULL CHECK(evaluation_year BETWEEN 1900 AND 2999),
  performance_grade TEXT NOT NULL,
  key_work_score REAL CHECK(key_work_score IS NULL OR key_work_score BETWEEN 0 AND 100),
  leadership_rating TEXT,
  remarks TEXT,
  raw_json TEXT NOT NULL,
  warning_message TEXT,
  created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE(batch_id,source_row_number),
  FOREIGN KEY(batch_id) REFERENCES org_performance_import_batches(id) ON DELETE RESTRICT,
  FOREIGN KEY(org_unit_id) REFERENCES org_units(id) ON DELETE RESTRICT
);
CREATE INDEX idx_org_performance_raw_current
  ON org_performance_raw_records(org_unit_id,evaluation_year,batch_id DESC,id DESC);

CREATE TABLE org_performance_import_errors (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  batch_id INTEGER NOT NULL,
  source_row_number INTEGER NOT NULL,
  org_code TEXT,
  raw_json TEXT NOT NULL,
  error_message TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(batch_id) REFERENCES org_performance_import_batches(id) ON DELETE RESTRICT
);
CREATE INDEX idx_org_performance_errors_batch
  ON org_performance_import_errors(batch_id,source_row_number);

CREATE TABLE org_performance_corrections (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  raw_record_id INTEGER NOT NULL,
  correction_scope TEXT NOT NULL
    CHECK(correction_scope IN('ALL','PERFORMANCE_GRADE','KEY_WORK_SCORE','LEADERSHIP_RATING')),
  original_values_json TEXT NOT NULL,
  corrected_grade TEXT,
  corrected_key_work_score REAL
    CHECK(corrected_key_work_score IS NULL OR corrected_key_work_score BETWEEN 0 AND 100),
  corrected_leadership_rating TEXT,
  correction_reason TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'DRAFT'
    CHECK(status IN('DRAFT','SUBMITTED','CONFIRMED','REJECTED')),
  row_version INTEGER NOT NULL DEFAULT 0,
  submitted_by INTEGER,
  submitted_at TEXT,
  reviewed_by INTEGER,
  reviewed_at TEXT,
  review_opinion TEXT,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(raw_record_id) REFERENCES org_performance_raw_records(id) ON DELETE RESTRICT,
  FOREIGN KEY(submitted_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(reviewed_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(updated_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_org_performance_corrections_record
  ON org_performance_corrections(raw_record_id,status,id DESC);

CREATE TABLE org_performance_correction_materials (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  correction_id INTEGER NOT NULL,
  attachment_id INTEGER NOT NULL UNIQUE,
  remarks TEXT,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(correction_id) REFERENCES org_performance_corrections(id) ON DELETE RESTRICT,
  FOREIGN KEY(attachment_id) REFERENCES sys_attachments(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL
);

CREATE TABLE org_performance_correction_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  correction_id INTEGER NOT NULL,
  from_status TEXT,
  to_status TEXT NOT NULL,
  opinion TEXT,
  operator_id INTEGER,
  changed_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(correction_id) REFERENCES org_performance_corrections(id) ON DELETE RESTRICT,
  FOREIGN KEY(operator_id) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_org_performance_history_correction
  ON org_performance_correction_history(correction_id,changed_at DESC);
