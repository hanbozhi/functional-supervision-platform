CREATE TABLE counterpart_relations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  subject_org_id INTEGER NOT NULL,
  counterpart_org_id INTEGER NOT NULL,
  collaboration_item TEXT NOT NULL,
  source TEXT NOT NULL CHECK (source IN ('MANUAL','RULE_SUGGESTION')),
  confidence REAL NOT NULL DEFAULT 100 CHECK (confidence BETWEEN 0 AND 100),
  status TEXT NOT NULL DEFAULT 'SUGGESTED'
    CHECK (status IN ('SUGGESTED','CONFIRMED','REJECTED','INACTIVE')),
  verification_opinion TEXT,
  verified_by INTEGER,
  verified_at TEXT,
  row_version INTEGER NOT NULL DEFAULT 0,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  CHECK (subject_org_id <> counterpart_org_id),
  UNIQUE(subject_org_id, counterpart_org_id, collaboration_item),
  FOREIGN KEY(subject_org_id) REFERENCES org_units(id) ON DELETE RESTRICT,
  FOREIGN KEY(counterpart_org_id) REFERENCES org_units(id) ON DELETE RESTRICT,
  FOREIGN KEY(verified_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(updated_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_counterpart_relations_query
  ON counterpart_relations(status, subject_org_id, counterpart_org_id);

CREATE TABLE counterpart_questionnaires (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  batch_code TEXT NOT NULL COLLATE NOCASE UNIQUE,
  title TEXT NOT NULL,
  evaluation_year INTEGER NOT NULL CHECK (evaluation_year BETWEEN 1900 AND 2999),
  deadline_at TEXT,
  description TEXT,
  indicator_version_id INTEGER,
  status TEXT NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','PUBLISHED','DEADLINE','CLOSED')),
  row_version INTEGER NOT NULL DEFAULT 0,
  published_by INTEGER,
  published_at TEXT,
  closed_by INTEGER,
  closed_at TEXT,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  updated_by INTEGER,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(indicator_version_id) REFERENCES indicator_versions(id) ON DELETE SET NULL,
  FOREIGN KEY(published_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(closed_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL,
  FOREIGN KEY(updated_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_counterpart_questionnaires_year_status
  ON counterpart_questionnaires(evaluation_year DESC, status);

CREATE TABLE counterpart_questionnaire_dimensions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  questionnaire_id INTEGER NOT NULL,
  dimension_code TEXT NOT NULL COLLATE NOCASE,
  dimension_name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  UNIQUE(questionnaire_id, dimension_code),
  FOREIGN KEY(questionnaire_id) REFERENCES counterpart_questionnaires(id) ON DELETE RESTRICT
);

CREATE TABLE counterpart_questionnaire_questions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  questionnaire_id INTEGER NOT NULL,
  dimension_id INTEGER,
  question_code TEXT NOT NULL COLLATE NOCASE,
  question_text TEXT NOT NULL,
  question_type TEXT NOT NULL CHECK (question_type IN ('SCORE','TEXT')),
  required INTEGER NOT NULL DEFAULT 1 CHECK (required IN (0,1)),
  indicator_item_id INTEGER,
  sort_order INTEGER NOT NULL DEFAULT 0,
  UNIQUE(questionnaire_id, question_code),
  FOREIGN KEY(questionnaire_id) REFERENCES counterpart_questionnaires(id) ON DELETE RESTRICT,
  FOREIGN KEY(dimension_id) REFERENCES counterpart_questionnaire_dimensions(id) ON DELETE RESTRICT,
  FOREIGN KEY(indicator_item_id) REFERENCES indicator_items(id) ON DELETE SET NULL
);
CREATE INDEX idx_counterpart_questions_questionnaire
  ON counterpart_questionnaire_questions(questionnaire_id, sort_order, id);

CREATE TABLE counterpart_questionnaire_recipients (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  questionnaire_id INTEGER NOT NULL,
  relation_id INTEGER NOT NULL,
  evaluator_org_id INTEGER NOT NULL,
  target_org_id INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','SENT','SUBMITTED','CANCELLED')),
  sent_at TEXT,
  submitted_at TEXT,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  UNIQUE(questionnaire_id, relation_id),
  FOREIGN KEY(questionnaire_id) REFERENCES counterpart_questionnaires(id) ON DELETE RESTRICT,
  FOREIGN KEY(relation_id) REFERENCES counterpart_relations(id) ON DELETE RESTRICT,
  FOREIGN KEY(evaluator_org_id) REFERENCES org_units(id) ON DELETE RESTRICT,
  FOREIGN KEY(target_org_id) REFERENCES org_units(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_counterpart_recipients_status
  ON counterpart_questionnaire_recipients(questionnaire_id, status);

CREATE TABLE counterpart_anonymous_mappings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  recipient_id INTEGER NOT NULL UNIQUE,
  anonymous_code TEXT NOT NULL COLLATE NOCASE UNIQUE,
  fill_token TEXT NOT NULL UNIQUE,
  restore_count INTEGER NOT NULL DEFAULT 0,
  last_restored_by INTEGER,
  last_restored_at TEXT,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(recipient_id) REFERENCES counterpart_questionnaire_recipients(id) ON DELETE RESTRICT,
  FOREIGN KEY(last_restored_by) REFERENCES sys_users(id) ON DELETE SET NULL
);

CREATE TABLE counterpart_questionnaire_responses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  recipient_id INTEGER NOT NULL UNIQUE,
  anonymous_code TEXT NOT NULL,
  started_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  submitted_at TEXT NOT NULL,
  client_elapsed_seconds INTEGER CHECK (client_elapsed_seconds IS NULL OR client_elapsed_seconds >= 0),
  FOREIGN KEY(recipient_id) REFERENCES counterpart_questionnaire_recipients(id) ON DELETE RESTRICT
);
CREATE INDEX idx_counterpart_responses_submitted
  ON counterpart_questionnaire_responses(submitted_at);

CREATE TABLE counterpart_questionnaire_answers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  response_id INTEGER NOT NULL,
  question_id INTEGER NOT NULL,
  score_value INTEGER CHECK (score_value IS NULL OR score_value BETWEEN 1 AND 5),
  text_value TEXT,
  UNIQUE(response_id, question_id),
  CHECK (score_value IS NOT NULL OR text_value IS NOT NULL),
  FOREIGN KEY(response_id) REFERENCES counterpart_questionnaire_responses(id) ON DELETE RESTRICT,
  FOREIGN KEY(question_id) REFERENCES counterpart_questionnaire_questions(id) ON DELETE RESTRICT
);

CREATE TABLE counterpart_push_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  questionnaire_id INTEGER NOT NULL,
  recipient_id INTEGER NOT NULL,
  channel TEXT NOT NULL DEFAULT 'SIMULATED_SMS',
  delivery_status TEXT NOT NULL CHECK (delivery_status IN ('DELIVERED','FAILED')),
  message_summary TEXT NOT NULL,
  failure_reason TEXT,
  sent_by INTEGER,
  sent_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(questionnaire_id) REFERENCES counterpart_questionnaires(id) ON DELETE RESTRICT,
  FOREIGN KEY(recipient_id) REFERENCES counterpart_questionnaire_recipients(id) ON DELETE RESTRICT,
  FOREIGN KEY(sent_by) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_counterpart_push_logs_batch
  ON counterpart_push_logs(questionnaire_id, sent_at DESC);

CREATE TABLE counterpart_anomaly_runs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  questionnaire_id INTEGER NOT NULL,
  run_code TEXT NOT NULL UNIQUE,
  rules_json TEXT NOT NULL,
  sample_count INTEGER NOT NULL DEFAULT 0,
  anomaly_count INTEGER NOT NULL DEFAULT 0,
  created_by INTEGER,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(questionnaire_id) REFERENCES counterpart_questionnaires(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES sys_users(id) ON DELETE SET NULL
);

CREATE TABLE counterpart_anomaly_cases (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  run_id INTEGER NOT NULL,
  response_id INTEGER NOT NULL,
  question_id INTEGER,
  anomaly_type TEXT NOT NULL
    CHECK (anomaly_type IN ('EXTREME_SCORE','MEAN_DEVIATION','RAPID_SUBMISSION')),
  observed_value REAL,
  reference_value REAL,
  rule_explanation TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','ASSIGNED','ACCEPTED','REJECTED')),
  assigned_to INTEGER,
  assigned_at TEXT,
  row_version INTEGER NOT NULL DEFAULT 0,
  UNIQUE(run_id, response_id, question_id, anomaly_type),
  FOREIGN KEY(run_id) REFERENCES counterpart_anomaly_runs(id) ON DELETE RESTRICT,
  FOREIGN KEY(response_id) REFERENCES counterpart_questionnaire_responses(id) ON DELETE RESTRICT,
  FOREIGN KEY(question_id) REFERENCES counterpart_questionnaire_questions(id) ON DELETE RESTRICT,
  FOREIGN KEY(assigned_to) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_counterpart_anomaly_cases_status
  ON counterpart_anomaly_cases(run_id, status, anomaly_type);

CREATE TABLE counterpart_anomaly_reviews (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  anomaly_case_id INTEGER NOT NULL,
  review_action TEXT NOT NULL CHECK (review_action IN ('ASSIGN','ACCEPT','REJECT')),
  review_opinion TEXT,
  reviewer_id INTEGER,
  reviewed_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
  FOREIGN KEY(anomaly_case_id) REFERENCES counterpart_anomaly_cases(id) ON DELETE RESTRICT,
  FOREIGN KEY(reviewer_id) REFERENCES sys_users(id) ON DELETE SET NULL
);
CREATE INDEX idx_counterpart_reviews_case
  ON counterpart_anomaly_reviews(anomaly_case_id, reviewed_at DESC);
