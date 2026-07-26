CREATE TABLE org_units (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  parent_id INTEGER,
  unit_code TEXT NOT NULL UNIQUE,
  unit_name TEXT NOT NULL,
  unit_short_name TEXT,
  unit_type TEXT NOT NULL,
  unit_level TEXT,
  organization_nature TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'DELETED')),
  created_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  updated_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  FOREIGN KEY (parent_id) REFERENCES org_units(id)
    ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE INDEX idx_org_units_parent ON org_units(parent_id);
CREATE INDEX idx_org_units_name ON org_units(unit_name);
CREATE INDEX idx_org_units_status ON org_units(status);

CREATE TABLE sys_users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT,
  display_name TEXT NOT NULL,
  phone TEXT,
  email TEXT,
  org_unit_id INTEGER,
  status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'DELETED')),
  created_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  updated_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  FOREIGN KEY (org_unit_id) REFERENCES org_units(id)
    ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE INDEX idx_sys_users_org_unit ON sys_users(org_unit_id);
CREATE INDEX idx_sys_users_status ON sys_users(status);

CREATE TABLE sys_roles (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  role_code TEXT NOT NULL UNIQUE,
  role_name TEXT NOT NULL,
  description TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'DELETED')),
  created_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  updated_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX idx_sys_roles_status ON sys_roles(status);

CREATE TABLE sys_user_roles (
  user_id INTEGER NOT NULL,
  role_id INTEGER NOT NULL,
  assigned_by INTEGER,
  assigned_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  PRIMARY KEY (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES sys_roles(id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  FOREIGN KEY (assigned_by) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_sys_user_roles_role ON sys_user_roles(role_id);

CREATE TABLE sys_attachments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  business_type TEXT NOT NULL,
  business_id INTEGER NOT NULL,
  original_name TEXT NOT NULL,
  stored_name TEXT NOT NULL,
  storage_path TEXT NOT NULL,
  content_type TEXT,
  extension TEXT,
  file_size INTEGER NOT NULL DEFAULT 0 CHECK (file_size >= 0),
  sha256 TEXT,
  version_no INTEGER NOT NULL DEFAULT 1 CHECK (version_no >= 1),
  uploaded_by INTEGER,
  status TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'DELETED')),
  created_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  updated_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  FOREIGN KEY (uploaded_by) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_sys_attachments_business
  ON sys_attachments(business_type, business_id);
CREATE INDEX idx_sys_attachments_sha256 ON sys_attachments(sha256);
CREATE INDEX idx_sys_attachments_uploader ON sys_attachments(uploaded_by);

CREATE TABLE sys_operation_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  module_code TEXT NOT NULL,
  business_type TEXT,
  business_id INTEGER,
  action TEXT NOT NULL,
  operator_id INTEGER,
  request_method TEXT,
  request_path TEXT,
  before_json TEXT,
  after_json TEXT,
  result TEXT NOT NULL DEFAULT 'SUCCESS'
    CHECK (result IN ('SUCCESS', 'FAILURE')),
  error_message TEXT,
  ip_address TEXT,
  created_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
  FOREIGN KEY (operator_id) REFERENCES sys_users(id)
    ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_sys_operation_logs_module_time
  ON sys_operation_logs(module_code, created_at);
CREATE INDEX idx_sys_operation_logs_business
  ON sys_operation_logs(business_type, business_id);
CREATE INDEX idx_sys_operation_logs_operator_time
  ON sys_operation_logs(operator_id, created_at);
