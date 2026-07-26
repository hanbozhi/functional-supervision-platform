CREATE TABLE indicator_systems (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  system_code TEXT NOT NULL COLLATE NOCASE UNIQUE,
  system_name TEXT NOT NULL,
  applicable_org_type TEXT NOT NULL,
  description TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_indicator_systems_status_name
  ON indicator_systems(status, system_name);

CREATE TABLE indicator_versions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  system_id INTEGER NOT NULL,
  evaluation_year INTEGER NOT NULL CHECK (evaluation_year BETWEEN 1900 AND 2999),
  version_no INTEGER NOT NULL CHECK (version_no >= 1),
  version_name TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
  source_version_id INTEGER,
  row_version INTEGER NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  published_by INTEGER,
  published_at TEXT,
  archived_by INTEGER,
  archived_at TEXT,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE (system_id, evaluation_year, version_no),
  FOREIGN KEY (system_id) REFERENCES indicator_systems(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (source_version_id) REFERENCES indicator_versions(id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (published_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (archived_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_indicator_versions_system_year
  ON indicator_versions(system_id, evaluation_year DESC, version_no DESC);
CREATE INDEX idx_indicator_versions_status ON indicator_versions(status);

CREATE TABLE indicator_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  version_id INTEGER NOT NULL,
  parent_id INTEGER,
  parent_version_id INTEGER,
  parent_level INTEGER,
  indicator_level INTEGER NOT NULL CHECK (indicator_level IN (1,2,3)),
  indicator_code TEXT NOT NULL COLLATE NOCASE,
  indicator_name TEXT NOT NULL,
  standard_score REAL NOT NULL DEFAULT 0 CHECK (standard_score >= 0),
  weight REAL NOT NULL DEFAULT 0 CHECK (weight >= 0 AND weight <= 100),
  indicator_type TEXT NOT NULL DEFAULT 'COMMON'
    CHECK (indicator_type IN ('COMMON','CUSTOM')),
  evaluation_method TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  row_version INTEGER NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE (version_id, indicator_code),
  UNIQUE (id, version_id, indicator_level),
  CHECK (
    (indicator_level = 1 AND parent_id IS NULL
      AND parent_version_id IS NULL AND parent_level IS NULL)
    OR
    (indicator_level > 1 AND parent_id IS NOT NULL
      AND parent_version_id = version_id
      AND parent_level = indicator_level - 1)
  ),
  FOREIGN KEY (version_id) REFERENCES indicator_versions(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (parent_id) REFERENCES indicator_items(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (parent_id, parent_version_id, parent_level)
    REFERENCES indicator_items(id, version_id, indicator_level)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_indicator_items_tree
  ON indicator_items(version_id, parent_id, status, sort_order, id);
CREATE INDEX idx_indicator_items_level
  ON indicator_items(version_id, indicator_level, status);

CREATE TABLE indicator_scoring_rules (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  indicator_id INTEGER NOT NULL,
  rule_type TEXT NOT NULL CHECK (
    rule_type IN ('THRESHOLD_DEDUCTION','STEP_SCORE','VETO')
  ),
  rule_name TEXT NOT NULL,
  config_json TEXT NOT NULL,
  description TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  row_version INTEGER NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY (indicator_id) REFERENCES indicator_items(id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_indicator_rules_indicator
  ON indicator_scoring_rules(indicator_id, status, sort_order, id);

CREATE TABLE indicator_templates (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  template_code TEXT NOT NULL COLLATE NOCASE UNIQUE,
  template_name TEXT NOT NULL,
  applicable_org_type TEXT NOT NULL,
  description TEXT,
  snapshot_json TEXT NOT NULL,
  source_version_id INTEGER,
  indicator_count INTEGER NOT NULL DEFAULT 0 CHECK (indicator_count >= 0),
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  row_version INTEGER NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY (source_version_id) REFERENCES indicator_versions(id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (created_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  FOREIGN KEY (updated_by) REFERENCES sys_users(id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_indicator_templates_status_type
  ON indicator_templates(status, applicable_org_type, template_name);
