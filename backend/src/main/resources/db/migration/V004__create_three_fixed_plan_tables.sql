CREATE TABLE three_fixed_plans (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  org_unit_id INTEGER NOT NULL UNIQUE,
  current_version_id INTEGER,
  status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE','INACTIVE')),
  created_by INTEGER,
  updated_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY (org_unit_id) REFERENCES org_units(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (current_version_id) REFERENCES three_fixed_plan_versions(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE TABLE three_fixed_plan_versions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  plan_id INTEGER NOT NULL,
  version_no INTEGER NOT NULL CHECK (version_no >= 1),
  version_label TEXT NOT NULL,
  source_type TEXT NOT NULL
    CHECK (source_type IN ('MANUAL','SINGLE_UPLOAD','BATCH_UPLOAD')),
  workflow_status TEXT NOT NULL DEFAULT 'PENDING_REVIEW'
    CHECK (workflow_status IN ('PENDING_REVIEW','RETURNED','CONFIRMED')),
  parse_status TEXT NOT NULL DEFAULT 'NOT_APPLICABLE'
    CHECK (parse_status IN ('NOT_APPLICABLE','SUCCESS','PARTIAL','FAILED')),
  plan_name TEXT NOT NULL,
  document_no TEXT,
  effective_date TEXT,
  organization_name TEXT,
  organization_nature TEXT,
  staffing_type TEXT,
  approved_staffing INTEGER CHECK (approved_staffing IS NULL OR approved_staffing >= 0),
  main_responsibilities TEXT,
  internal_departments TEXT,
  remarks TEXT,
  parsed_text TEXT,
  review_opinion TEXT,
  created_by INTEGER,
  updated_by INTEGER,
  reviewed_by INTEGER,
  reviewed_at TEXT,
  row_version INTEGER NOT NULL DEFAULT 1 CHECK (row_version >= 1),
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE (plan_id, version_no),
  UNIQUE (plan_id, version_label),
  FOREIGN KEY (plan_id) REFERENCES three_fixed_plans(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (reviewed_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE UNIQUE INDEX idx_three_fixed_versions_one_open
  ON three_fixed_plan_versions(plan_id)
  WHERE workflow_status <> 'CONFIRMED';
CREATE INDEX idx_three_fixed_versions_status
  ON three_fixed_plan_versions(workflow_status, updated_at);

CREATE TABLE three_fixed_parse_results (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  version_id INTEGER NOT NULL,
  field_code TEXT NOT NULL,
  source_label TEXT,
  extracted_value TEXT,
  corrected_value TEXT,
  source_snippet TEXT,
  parse_method TEXT NOT NULL
    CHECK (parse_method IN ('LABEL','TABLE','MANUAL')),
  confidence_code TEXT NOT NULL DEFAULT 'LOW'
    CHECK (confidence_code IN ('HIGH','MEDIUM','LOW')),
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE (version_id, field_code),
  FOREIGN KEY (version_id) REFERENCES three_fixed_plan_versions(id)
    ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX idx_three_fixed_parse_results_version
  ON three_fixed_parse_results(version_id);

CREATE TABLE three_fixed_field_mappings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  file_type TEXT NOT NULL CHECK (file_type IN ('ALL','XLSX','DOCX','PDF')),
  source_label TEXT NOT NULL,
  target_field TEXT NOT NULL CHECK (target_field IN (
    'PLAN_NAME','DOCUMENT_NO','EFFECTIVE_DATE','ORGANIZATION_NAME',
    'ORGANIZATION_NATURE','STAFFING_TYPE','APPROVED_STAFFING',
    'MAIN_RESPONSIBILITIES','INTERNAL_DEPARTMENTS','REMARKS'
  )),
  sort_order INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_by INTEGER,
  updated_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE (file_type, source_label COLLATE NOCASE),
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);
CREATE INDEX idx_three_fixed_mappings_target
  ON three_fixed_field_mappings(target_field, status);

INSERT INTO three_fixed_field_mappings(file_type, source_label, target_field, sort_order)
VALUES
  ('ALL','方案名称','PLAN_NAME',10),
  ('ALL','文件名称','PLAN_NAME',11),
  ('ALL','文号','DOCUMENT_NO',20),
  ('ALL','发文字号','DOCUMENT_NO',21),
  ('ALL','生效日期','EFFECTIVE_DATE',30),
  ('ALL','机构名称','ORGANIZATION_NAME',40),
  ('ALL','单位名称','ORGANIZATION_NAME',41),
  ('ALL','机构性质','ORGANIZATION_NATURE',50),
  ('ALL','编制类型','STAFFING_TYPE',60),
  ('ALL','核定编制','APPROVED_STAFFING',70),
  ('ALL','核定编制数','APPROVED_STAFFING',71),
  ('ALL','主要职责','MAIN_RESPONSIBILITIES',80),
  ('ALL','内设机构','INTERNAL_DEPARTMENTS',90),
  ('ALL','备注','REMARKS',100);
