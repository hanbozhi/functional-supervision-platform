CREATE TABLE public_service_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  item_code TEXT NOT NULL UNIQUE COLLATE NOCASE,
  item_name TEXT NOT NULL,
  org_unit_id INTEGER NOT NULL,
  description TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE')),
  created_by INTEGER,
  updated_by INTEGER,
  created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(org_unit_id) REFERENCES org_units(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(updated_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_public_service_items_org ON public_service_items(org_unit_id,status);

CREATE TABLE public_evaluations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  evaluation_no TEXT NOT NULL UNIQUE,
  source TEXT NOT NULL CHECK(source IN('LOCAL','HOTLINE_12345','GOV_SERVICE','GOV_PLATFORM')),
  source_record_id TEXT,
  import_batch_id INTEGER,
  org_unit_id INTEGER NOT NULL,
  service_item_id INTEGER,
  source_service_item TEXT,
  convenience_score INTEGER CHECK(convenience_score BETWEEN 1 AND 5),
  attitude_score INTEGER CHECK(attitude_score BETWEEN 1 AND 5),
  timeliness_score INTEGER CHECK(timeliness_score BETWEEN 1 AND 5),
  clarity_score INTEGER CHECK(clarity_score BETWEEN 1 AND 5),
  overall_score REAL NOT NULL CHECK(overall_score BETWEEN 1 AND 5),
  comment_text TEXT,
  sentiment TEXT NOT NULL DEFAULT 'NEUTRAL' CHECK(sentiment IN('POSITIVE','NEUTRAL','NEGATIVE')),
  sentiment_source TEXT NOT NULL DEFAULT 'RULE' CHECK(sentiment_source IN('RULE','MANUAL')),
  is_anonymous INTEGER NOT NULL DEFAULT 1 CHECK(is_anonymous IN(0,1)),
  evaluator_name TEXT,
  evaluator_phone TEXT,
  evaluator_id_no TEXT,
  evaluated_at TEXT NOT NULL,
  process_status TEXT NOT NULL DEFAULT 'PENDING' CHECK(process_status IN('PENDING','PROCESSING','RESOLVED','IGNORED')),
  process_opinion TEXT,
  processed_by INTEGER,
  processed_at TEXT,
  raw_json TEXT,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(org_unit_id) REFERENCES org_units(id) ON DELETE RESTRICT,
  FOREIGN KEY(service_item_id) REFERENCES public_service_items(id) ON DELETE SET NULL,
  FOREIGN KEY(processed_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_public_evaluations_query ON public_evaluations(org_unit_id,source,evaluated_at DESC);
CREATE INDEX idx_public_evaluations_status ON public_evaluations(process_status,sentiment);

CREATE TABLE public_evaluation_attachments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  evaluation_id INTEGER NOT NULL,
  attachment_id INTEGER NOT NULL UNIQUE,
  created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(evaluation_id) REFERENCES public_evaluations(id) ON DELETE RESTRICT,
  FOREIGN KEY(attachment_id) REFERENCES sys_attachments(id) ON DELETE RESTRICT
);

CREATE TABLE public_privacy_access_requests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  evaluation_id INTEGER NOT NULL,
  reason TEXT NOT NULL,
  requested_fields TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','APPROVED','REJECTED')),
  requested_by INTEGER,
  requested_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  reviewed_by INTEGER,
  reviewed_at TEXT,
  review_opinion TEXT,
  FOREIGN KEY(evaluation_id) REFERENCES public_evaluations(id) ON DELETE RESTRICT,
  FOREIGN KEY(requested_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(reviewed_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_public_privacy_requests_eval ON public_privacy_access_requests(evaluation_id,status,id DESC);

CREATE TABLE public_privacy_access_audits (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  request_id INTEGER NOT NULL,
  evaluation_id INTEGER NOT NULL,
  accessed_by INTEGER,
  accessed_fields TEXT NOT NULL,
  accessed_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(request_id) REFERENCES public_privacy_access_requests(id) ON DELETE RESTRICT,
  FOREIGN KEY(evaluation_id) REFERENCES public_evaluations(id) ON DELETE RESTRICT,
  FOREIGN KEY(accessed_by) REFERENCES sys_users(id) ON DELETE SET NULL
);

CREATE TABLE public_evaluation_import_batches (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  batch_code TEXT NOT NULL UNIQUE,
  source TEXT NOT NULL CHECK(source IN('HOTLINE_12345','GOV_SERVICE','GOV_PLATFORM')),
  original_file_name TEXT NOT NULL,
  file_type TEXT NOT NULL CHECK(file_type IN('XLSX','CSV','JSON')),
  file_size INTEGER NOT NULL CHECK(file_size>=0),
  total_rows INTEGER NOT NULL DEFAULT 0,
  success_rows INTEGER NOT NULL DEFAULT 0,
  failed_rows INTEGER NOT NULL DEFAULT 0,
  warning_rows INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'PROCESSING' CHECK(status IN('PROCESSING','COMPLETED','PARTIAL_FAILED','FAILED')),
  imported_by INTEGER,
  imported_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(imported_by) REFERENCES sys_users(id) ON DELETE SET NULL
);

CREATE TABLE public_evaluation_import_errors (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  batch_id INTEGER NOT NULL,
  source_row_number INTEGER NOT NULL,
  org_code TEXT,
  raw_json TEXT NOT NULL,
  error_message TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT(strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(batch_id) REFERENCES public_evaluation_import_batches(id) ON DELETE RESTRICT
);
CREATE INDEX idx_public_evaluation_import_errors ON public_evaluation_import_errors(batch_id,source_row_number);
