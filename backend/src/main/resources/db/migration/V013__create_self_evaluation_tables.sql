CREATE TABLE self_evaluation_tasks (
 id INTEGER PRIMARY KEY AUTOINCREMENT,task_code TEXT NOT NULL UNIQUE COLLATE NOCASE,task_name TEXT NOT NULL,
 evaluation_year INTEGER NOT NULL CHECK(evaluation_year BETWEEN 1900 AND 2999),
 task_type TEXT NOT NULL CHECK(task_type IN('ANNUAL','SPECIAL')),start_date TEXT NOT NULL,end_date TEXT NOT NULL,
 description TEXT,indicator_version_id INTEGER NOT NULL,
 status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(status IN('DRAFT','PUBLISHED','FILLING','SUBMITTED','RETURNED','COMPLETED','CANCELLED')),
 published_by INTEGER,published_at TEXT,created_by INTEGER,updated_by INTEGER,
 created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),updated_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
 FOREIGN KEY(indicator_version_id) REFERENCES indicator_versions(id) ON DELETE RESTRICT,
 FOREIGN KEY(published_by) REFERENCES sys_users(id) ON DELETE SET NULL,FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL,FOREIGN KEY(updated_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE TABLE self_evaluation_task_orgs (
 id INTEGER PRIMARY KEY AUTOINCREMENT,task_id INTEGER NOT NULL,org_unit_id INTEGER NOT NULL,
 status TEXT NOT NULL DEFAULT 'NOT_STARTED' CHECK(status IN('NOT_STARTED','DRAFT','SUBMITTED','RETURNED','COMPLETED')),
 total_score REAL NOT NULL DEFAULT 0,completed_items INTEGER NOT NULL DEFAULT 0,total_items INTEGER NOT NULL DEFAULT 0,
 submitted_by INTEGER,submitted_at TEXT,reviewed_by INTEGER,reviewed_at TEXT,review_opinion TEXT,
 created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),updated_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
 UNIQUE(task_id,org_unit_id),FOREIGN KEY(task_id) REFERENCES self_evaluation_tasks(id) ON DELETE RESTRICT,
 FOREIGN KEY(org_unit_id) REFERENCES org_units(id) ON DELETE RESTRICT,FOREIGN KEY(submitted_by) REFERENCES sys_users(id) ON DELETE SET NULL,FOREIGN KEY(reviewed_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE TABLE self_evaluation_indicator_snapshots (
 id INTEGER PRIMARY KEY AUTOINCREMENT,task_id INTEGER NOT NULL,source_indicator_id INTEGER,
 indicator_code TEXT NOT NULL,indicator_name TEXT NOT NULL,standard_score REAL NOT NULL,weight REAL NOT NULL,
 required_material_count INTEGER NOT NULL DEFAULT 1 CHECK(required_material_count>=0),
 allowed_extensions TEXT NOT NULL DEFAULT 'pdf,doc,docx,xls,xlsx,jpg,jpeg,png,zip',
 naming_keywords TEXT,due_date TEXT,sort_order INTEGER NOT NULL DEFAULT 0,
 UNIQUE(task_id,indicator_code),FOREIGN KEY(task_id) REFERENCES self_evaluation_tasks(id) ON DELETE RESTRICT
);
CREATE TABLE self_evaluation_entries (
 id INTEGER PRIMARY KEY AUTOINCREMENT,task_org_id INTEGER NOT NULL,snapshot_id INTEGER NOT NULL,
 self_score REAL CHECK(self_score IS NULL OR self_score>=0),performance_description TEXT,completion_status TEXT,
 row_version INTEGER NOT NULL DEFAULT 0,updated_by INTEGER,
 updated_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
 UNIQUE(task_org_id,snapshot_id),FOREIGN KEY(task_org_id) REFERENCES self_evaluation_task_orgs(id) ON DELETE RESTRICT,
 FOREIGN KEY(snapshot_id) REFERENCES self_evaluation_indicator_snapshots(id) ON DELETE RESTRICT,FOREIGN KEY(updated_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE TABLE self_evaluation_materials (
 id INTEGER PRIMARY KEY AUTOINCREMENT,entry_id INTEGER NOT NULL,attachment_id INTEGER NOT NULL UNIQUE,
 version_group TEXT NOT NULL,version_no INTEGER NOT NULL DEFAULT 1,material_name TEXT NOT NULL,category TEXT NOT NULL DEFAULT 'OTHER',
 description TEXT,classification_source TEXT NOT NULL DEFAULT 'MANUAL' CHECK(classification_source IN('RULE','MANUAL')),
 document_date TEXT,
 classification_status TEXT NOT NULL DEFAULT 'CONFIRMED' CHECK(classification_status IN('SUGGESTED','CONFIRMED')),
 is_current INTEGER NOT NULL DEFAULT 1 CHECK(is_current IN(0,1)),status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE')),
 uploaded_by INTEGER,uploaded_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
 FOREIGN KEY(entry_id) REFERENCES self_evaluation_entries(id) ON DELETE RESTRICT,FOREIGN KEY(attachment_id) REFERENCES sys_attachments(id) ON DELETE RESTRICT,FOREIGN KEY(uploaded_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX uq_self_material_current ON self_evaluation_materials(version_group) WHERE is_current=1 AND status='ACTIVE';
CREATE INDEX idx_self_material_entry ON self_evaluation_materials(entry_id,status,is_current);
CREATE TABLE self_evaluation_status_history (
 id INTEGER PRIMARY KEY AUTOINCREMENT,business_type TEXT NOT NULL CHECK(business_type IN('TASK','TASK_ORG')),
 business_id INTEGER NOT NULL,from_status TEXT,to_status TEXT NOT NULL,opinion TEXT,operator_id INTEGER,
 changed_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),FOREIGN KEY(operator_id) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE TABLE self_evaluation_warning_runs (
 id INTEGER PRIMARY KEY AUTOINCREMENT,task_id INTEGER NOT NULL,org_unit_id INTEGER,
 status TEXT NOT NULL DEFAULT 'COMPLETED',total_checks INTEGER NOT NULL DEFAULT 0,warning_count INTEGER NOT NULL DEFAULT 0,
 created_by INTEGER,created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
 FOREIGN KEY(task_id) REFERENCES self_evaluation_tasks(id) ON DELETE RESTRICT,FOREIGN KEY(org_unit_id) REFERENCES org_units(id) ON DELETE RESTRICT,FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE TABLE self_evaluation_warnings (
 id INTEGER PRIMARY KEY AUTOINCREMENT,run_id INTEGER NOT NULL,task_org_id INTEGER NOT NULL,snapshot_id INTEGER NOT NULL,
 warning_type TEXT NOT NULL CHECK(warning_type IN('MISSING','INSUFFICIENT','FORMAT','NAMING','EXPIRED','OVERDUE')),
 message TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN('OPEN','CONFIRMED','IGNORED','RESOLVED')),
 process_opinion TEXT,processed_by INTEGER,processed_at TEXT,
 FOREIGN KEY(run_id) REFERENCES self_evaluation_warning_runs(id) ON DELETE RESTRICT,FOREIGN KEY(task_org_id) REFERENCES self_evaluation_task_orgs(id) ON DELETE RESTRICT,
 FOREIGN KEY(snapshot_id) REFERENCES self_evaluation_indicator_snapshots(id) ON DELETE RESTRICT,FOREIGN KEY(processed_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_self_warnings_query ON self_evaluation_warnings(task_org_id,status,warning_type);
CREATE TABLE self_evaluation_reminder_logs (
 id INTEGER PRIMARY KEY AUTOINCREMENT,warning_id INTEGER NOT NULL,channel TEXT NOT NULL DEFAULT 'LOCAL_SIMULATION',
 recipient TEXT,content TEXT NOT NULL,sent_by INTEGER,sent_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
 FOREIGN KEY(warning_id) REFERENCES self_evaluation_warnings(id) ON DELETE RESTRICT,FOREIGN KEY(sent_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
