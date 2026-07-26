CREATE TABLE department_core_functions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  org_unit_id INTEGER NOT NULL,
  function_code TEXT NOT NULL,
  function_name TEXT NOT NULL,
  industry_tag TEXT,
  description TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  version_no INTEGER NOT NULL DEFAULT 1 CHECK (version_no >= 1),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE (org_unit_id, function_code COLLATE NOCASE),
  FOREIGN KEY (org_unit_id) REFERENCES org_units(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);
CREATE INDEX idx_core_functions_org_status
  ON department_core_functions(org_unit_id, status, sort_order);

CREATE TABLE department_duty_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  core_function_id INTEGER NOT NULL,
  org_unit_id INTEGER NOT NULL,
  duty_content TEXT NOT NULL,
  keywords TEXT,
  source_type TEXT NOT NULL CHECK (source_type IN ('THREE_FIXED','MANUAL')),
  source_version_id INTEGER,
  source_snippet TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE','INACTIVE','SUPERSEDED')),
  version_no INTEGER NOT NULL DEFAULT 1 CHECK (version_no >= 1),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY (core_function_id) REFERENCES department_core_functions(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (org_unit_id) REFERENCES org_units(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (source_version_id) REFERENCES three_fixed_plan_versions(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);
CREATE INDEX idx_duty_items_org_status
  ON department_duty_items(org_unit_id, status, sort_order);
CREATE INDEX idx_duty_items_function
  ON department_duty_items(core_function_id, status, sort_order);
CREATE INDEX idx_duty_items_source_version
  ON department_duty_items(source_version_id);

CREATE TABLE org_rights_department_mappings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  org_unit_id INTEGER NOT NULL,
  rights_department_name TEXT NOT NULL COLLATE NOCASE,
  mapping_type TEXT NOT NULL CHECK (mapping_type IN ('AUTO','MANUAL')),
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE (rights_department_name COLLATE NOCASE),
  FOREIGN KEY (org_unit_id) REFERENCES org_units(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);
CREATE INDEX idx_rights_department_mappings_org_status
  ON org_rights_department_mappings(org_unit_id, status);

CREATE TABLE duty_match_runs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  org_unit_id INTEGER NOT NULL,
  source_version_id INTEGER,
  rights_dataset_signature TEXT NOT NULL,
  match_threshold INTEGER NOT NULL DEFAULT 50
    CHECK (match_threshold BETWEEN 0 AND 100),
  duty_count INTEGER NOT NULL DEFAULT 0,
  rights_item_count INTEGER NOT NULL DEFAULT 0,
  matched_duty_count INTEGER NOT NULL DEFAULT 0,
  duty_missing_count INTEGER NOT NULL DEFAULT 0,
  unapproved_new_count INTEGER NOT NULL DEFAULT 0,
  coverage_rate REAL NOT NULL DEFAULT 0,
  match_rate REAL NOT NULL DEFAULT 0,
  status TEXT NOT NULL CHECK (status IN ('COMPLETED','FAILED')),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY (org_unit_id) REFERENCES org_units(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (source_version_id) REFERENCES three_fixed_plan_versions(id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);
CREATE INDEX idx_duty_match_runs_org_time
  ON duty_match_runs(org_unit_id, created_at DESC, id DESC);

CREATE TABLE duty_match_results (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  run_id INTEGER NOT NULL,
  duty_item_id INTEGER,
  rights_item_id INTEGER,
  result_type TEXT NOT NULL
    CHECK (result_type IN ('MATCHED','DUTY_MISSING','UNAPPROVED_NEW_DUTY')),
  match_origin TEXT NOT NULL DEFAULT 'AUTO' CHECK (match_origin IN ('AUTO','MANUAL')),
  duty_content_snapshot TEXT,
  rights_department_snapshot TEXT,
  rights_item_name_snapshot TEXT,
  rights_content_snapshot TEXT,
  auto_score REAL NOT NULL DEFAULT 0 CHECK (auto_score BETWEEN 0 AND 100),
  final_score REAL NOT NULL DEFAULT 0 CHECK (final_score BETWEEN 0 AND 100),
  matched_keywords TEXT,
  review_status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (review_status IN ('PENDING','CONFIRMED','REJECTED','ADJUSTED')),
  processing_opinion TEXT,
  reviewed_by INTEGER,
  reviewed_at TEXT,
  version_no INTEGER NOT NULL DEFAULT 1 CHECK (version_no >= 1),
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY (run_id) REFERENCES duty_match_runs(id) ON UPDATE CASCADE ON DELETE CASCADE,
  FOREIGN KEY (duty_item_id) REFERENCES department_duty_items(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (reviewed_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);
CREATE INDEX idx_duty_match_results_run_type
  ON duty_match_results(run_id, result_type, review_status);
CREATE INDEX idx_duty_match_results_duty
  ON duty_match_results(duty_item_id);
CREATE INDEX idx_duty_match_results_rights_logical
  ON duty_match_results(rights_item_id);
