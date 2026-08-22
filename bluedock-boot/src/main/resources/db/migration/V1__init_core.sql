-- 核心表首版。前缀 bluedock_。详见 docs/data/database.md

CREATE TABLE bluedock_users (
  id BIGINT NOT NULL PRIMARY KEY,
  identity VARCHAR(255) NULL DEFAULT '',
  name_az VARCHAR(10) NULL DEFAULT '',
  email VARCHAR(100) NULL DEFAULT '',
  nickname VARCHAR(255) NULL DEFAULT '',
  profession VARCHAR(255) NULL DEFAULT '',
  user_img VARCHAR(512) NULL DEFAULT '',
  telephone VARCHAR(20) NULL DEFAULT '',
  birthday VARCHAR(10) NULL DEFAULT '',
  address VARCHAR(100) NULL DEFAULT '',
  introduction VARCHAR(500) NULL DEFAULT '',
  password_encrypt VARCHAR(64) NULL DEFAULT '',
  password VARCHAR(100) NULL DEFAULT '',
  must_change_password TINYINT NULL DEFAULT 0,
  login_count INT NULL DEFAULT 0,
  last_ip VARCHAR(45) NULL DEFAULT '',
  last_at DATETIME(3) NULL,
  online_ip VARCHAR(45) NULL DEFAULT '',
  online_at DATETIME(3) NULL,
  task_dialog_id BIGINT NULL DEFAULT 0,
  created_ip VARCHAR(45) NULL DEFAULT '',
  disable_at DATETIME(3) NULL,
  email_verify TINYINT NULL DEFAULT 0,
  is_bot TINYINT NULL DEFAULT 0,
  lang VARCHAR(20) NULL DEFAULT '',
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_telephone (telephone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_devices (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  hash VARCHAR(64) NULL DEFAULT '',
  detail TEXT NULL,
  expired_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_user_devices_user_id (user_id),
  KEY idx_user_devices_hash (hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_email_verifications (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  code VARCHAR(64) NOT NULL DEFAULT '',
  email VARCHAR(100) NOT NULL DEFAULT '',
  type VARCHAR(16) NOT NULL DEFAULT 'reg' COMMENT 'reg|edit|delete',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending / 1 used',
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_email_verifications_code (code),
  KEY idx_user_email_verifications_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_deletes (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  email VARCHAR(100) NOT NULL DEFAULT '',
  nickname VARCHAR(255) NULL DEFAULT '',
  reason VARCHAR(500) NULL DEFAULT '',
  cache TEXT NULL COMMENT '注销前用户快照 JSON',
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  KEY idx_user_deletes_email (email),
  KEY idx_user_deletes_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_push_aliases (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  alias VARCHAR(64) NOT NULL DEFAULT '',
  platform VARCHAR(16) NOT NULL DEFAULT '',
  user_agent VARCHAR(512) NULL,
  device VARCHAR(128) NULL,
  device_hash VARCHAR(64) NULL,
  version VARCHAR(64) NULL,
  is_notified TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_push_aliases_alias_platform (alias, platform),
  KEY idx_user_push_aliases_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_app_push_logs (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  platform VARCHAR(16) NOT NULL DEFAULT '',
  alias VARCHAR(200) NOT NULL DEFAULT '',
  title VARCHAR(500) NOT NULL DEFAULT '',
  body VARCHAR(2000) NOT NULL DEFAULT '',
  request_body TEXT NULL,
  response_body TEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT '',
  skip_reason VARCHAR(64) NULL DEFAULT '',
  event_id VARCHAR(64) NULL DEFAULT '',
  message_id BIGINT NOT NULL DEFAULT 0,
  dialog_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  KEY idx_app_push_logs_created (created_at),
  KEY idx_app_push_logs_user (user_id),
  KEY idx_app_push_logs_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_settings (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(100) NOT NULL DEFAULT '',
  description VARCHAR(500) NULL,
  setting LONGTEXT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_settings_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_outbox (
  id BIGINT NOT NULL PRIMARY KEY,
  topic VARCHAR(128) NOT NULL,
  message_key VARCHAR(256) NULL,
  payload LONGTEXT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  published_at DATETIME(3) NULL,
  KEY idx_outbox_unpublished (published_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_projects (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(255) NOT NULL DEFAULT '',
  description VARCHAR(500) NULL,
  user_id BIGINT NOT NULL DEFAULT 0,
  is_personal TINYINT NOT NULL DEFAULT 0,
  dialog_id BIGINT NOT NULL DEFAULT 0,
  archive_method VARCHAR(20) NOT NULL DEFAULT 'system',
  archive_days INT NOT NULL DEFAULT 30,
  ai_auto_analyze VARCHAR(16) NOT NULL DEFAULT 'open',
  department_owner_view TINYINT NOT NULL DEFAULT 1,
  task_template_share VARCHAR(16) NOT NULL DEFAULT 'open',
  archived_at DATETIME(3) NULL,
  archived_user_id BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_projects_user_id (user_id),
  KEY idx_projects_archive_method (archive_method)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_project_users (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  owner TINYINT NOT NULL DEFAULT 0,
  top_at DATETIME(3) NULL,
  sort INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_project_users (project_id, user_id),
  KEY idx_project_users_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_project_permissions (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL,
  permissions LONGTEXT NOT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_project_permissions_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_project_columns (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL DEFAULT '',
  color VARCHAR(32) NULL DEFAULT '',
  sort INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_project_columns_project (project_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_project_flows (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL DEFAULT '',
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_project_flows_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_project_flow_items (
  id BIGINT NOT NULL PRIMARY KEY,
  flow_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'progress',
  color VARCHAR(32) NULL DEFAULT '',
  sort INT NOT NULL DEFAULT 0,
  turns VARCHAR(500) NULL DEFAULT '',
  user_ids VARCHAR(1000) NULL DEFAULT '',
  user_type VARCHAR(32) NULL DEFAULT '',
  column_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_flow_items_flow (flow_id, sort),
  KEY idx_flow_items_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_project_tags (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(20) NOT NULL DEFAULT '',
  color VARCHAR(32) NULL DEFAULT '',
  sort INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_project_tags_project (project_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_project_logs (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL DEFAULT 0,
  column_id BIGINT NOT NULL DEFAULT 0,
  task_id BIGINT NOT NULL DEFAULT 0,
  task_only TINYINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  detail VARCHAR(500) NOT NULL DEFAULT '',
  record LONGTEXT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  KEY idx_project_logs_project (project_id, task_only, created_at),
  KEY idx_project_logs_task (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_tasks (
  id BIGINT NOT NULL PRIMARY KEY,
  parent_id BIGINT NOT NULL DEFAULT 0,
  project_id BIGINT NOT NULL,
  column_id BIGINT NOT NULL DEFAULT 0,
  dialog_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(500) NOT NULL DEFAULT '',
  color VARCHAR(32) NULL DEFAULT '',
  description VARCHAR(2000) NULL,
  start_at DATETIME(3) NULL,
  end_at DATETIME(3) NULL,
  complete_at DATETIME(3) NULL,
  visibility TINYINT NOT NULL DEFAULT 1,
  priority_level INT NULL DEFAULT 0,
  priority_name VARCHAR(50) NULL DEFAULT '',
  priority_color VARCHAR(32) NULL DEFAULT '',
  flow_item_id BIGINT NOT NULL DEFAULT 0,
  flow_item_name VARCHAR(100) NULL DEFAULT '',
  sort INT NOT NULL DEFAULT 0,
  `loop` TINYINT NOT NULL DEFAULT 0,
  loop_at DATETIME(3) NULL,
  user_id BIGINT NOT NULL DEFAULT 0,
  archived_at DATETIME(3) NULL,
  archived_user_id BIGINT NULL,
  archived_follow TINYINT NOT NULL DEFAULT 0,
  deleted_at DATETIME(3) NULL,
  deleted_user_id BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  KEY idx_tasks_project_column (project_id, column_id, sort),
  KEY idx_tasks_parent (parent_id),
  KEY idx_tasks_end_at (end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_task_users (
  id BIGINT NOT NULL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  parent_task_id BIGINT NOT NULL DEFAULT 0,
  project_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  owner TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_task_users (task_id, user_id),
  KEY idx_task_users_user_id (user_id),
  KEY idx_task_users_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_task_visibility_users (
  id BIGINT NOT NULL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_task_visibility_users (task_id, user_id),
  KEY idx_task_visibility_user (user_id),
  KEY idx_task_visibility_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_task_tags (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_task_tags (task_id, tag_id),
  KEY idx_task_tags_tag (tag_id),
  KEY idx_task_tags_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_task_files (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL DEFAULT 0,
  task_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(255) NOT NULL DEFAULT '',
  size BIGINT NOT NULL DEFAULT 0,
  extension VARCHAR(32) NULL DEFAULT '',
  path VARCHAR(1024) NULL DEFAULT '',
  thumbnail VARCHAR(1024) NULL DEFAULT '',
  user_id BIGINT NOT NULL DEFAULT 0,
  download_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_task_files_task (task_id),
  KEY idx_task_files_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_task_relations (
  id BIGINT NOT NULL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  related_task_id BIGINT NOT NULL,
  direction VARCHAR(32) NOT NULL DEFAULT 'mention',
  dialog_id BIGINT NOT NULL DEFAULT 0,
  message_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_task_relations (task_id, related_task_id, direction),
  KEY idx_task_relations_task (task_id, direction),
  KEY idx_task_relations_related (related_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_task_contents (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL DEFAULT 0,
  task_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL DEFAULT 0,
  description VARCHAR(500) NULL DEFAULT '',
  content MEDIUMTEXT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  KEY idx_task_contents_task (task_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_task_templates (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL DEFAULT '',
  title VARCHAR(255) NULL DEFAULT '',
  content MEDIUMTEXT NULL,
  sort INT NOT NULL DEFAULT 0,
  is_default TINYINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  use_count INT NOT NULL DEFAULT 0,
  last_used_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  KEY idx_task_templates_project (project_id, sort),
  KEY idx_task_templates_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_task_ai_events (
  id BIGINT NOT NULL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  retry_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
  result JSON NULL,
  error TEXT NULL,
  message_id BIGINT NOT NULL DEFAULT 0,
  executed_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_task_ai_event (task_id, event_type),
  KEY idx_task_ai_status (status),
  KEY idx_task_ai_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_project_invites (
  id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL DEFAULT 0,
  expired_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_project_invites_code (code),
  KEY idx_project_invites_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_dialogs (
  id BIGINT NOT NULL PRIMARY KEY,
  type VARCHAR(20) NOT NULL DEFAULT 'user',
  group_type VARCHAR(20) NULL DEFAULT '',
  name VARCHAR(255) NULL DEFAULT '',
  avatar VARCHAR(512) NULL DEFAULT '',
  owner_id BIGINT NOT NULL DEFAULT 0,
  link_id BIGINT NOT NULL DEFAULT 0,
  last_message VARCHAR(500) NULL DEFAULT '',
  last_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_dialogs_last_at (last_at),
  KEY idx_dialogs_link (group_type, link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_dialog_users (
  id BIGINT NOT NULL PRIMARY KEY,
  dialog_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  unread_count INT NOT NULL DEFAULT 0,
  mention_count INT NOT NULL DEFAULT 0,
  mention_ids VARCHAR(2000) NOT NULL DEFAULT '',
  last_read_message_id BIGINT NOT NULL DEFAULT 0,
  is_top TINYINT NOT NULL DEFAULT 0,
  is_hidden TINYINT NOT NULL DEFAULT 0,
  is_muted TINYINT NOT NULL DEFAULT 0,
  mark_unread TINYINT NOT NULL DEFAULT 0,
  color VARCHAR(32) NOT NULL DEFAULT '',
  tag VARCHAR(64) NOT NULL DEFAULT '',
  is_deputy TINYINT NOT NULL DEFAULT 0,
  session_key VARCHAR(64) NOT NULL DEFAULT '',
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_dialog_users (dialog_id, user_id),
  KEY idx_dialog_users_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_dialog_messages (
  id BIGINT NOT NULL PRIMARY KEY,
  dialog_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL DEFAULT 0,
  type VARCHAR(32) NOT NULL DEFAULT 'text',
  body LONGTEXT NULL,
  reply_id BIGINT NOT NULL DEFAULT 0,
  tag_user_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_dialog_msgs_dialog (dialog_id, id),
  KEY idx_dialog_msgs_created (dialog_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_dialog_message_reads (
  id BIGINT NOT NULL PRIMARY KEY,
  message_id BIGINT NOT NULL,
  dialog_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  read_at DATETIME(3) NULL,
  is_silent TINYINT NOT NULL DEFAULT 0,
  email_sent TINYINT NOT NULL DEFAULT 0,
  dot TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  UNIQUE KEY uk_dialog_msg_reads (message_id, user_id),
  KEY idx_dialog_msg_reads_dialog_user (dialog_id, user_id),
  KEY idx_dialog_msg_reads_email (user_id, email_sent, is_silent, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_dialog_message_emojis (
  id BIGINT NOT NULL PRIMARY KEY,
  message_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  symbol VARCHAR(64) NOT NULL DEFAULT '',
  created_at DATETIME(3) NULL,
  UNIQUE KEY uk_dialog_msg_emojis (message_id, user_id, symbol),
  KEY idx_dialog_msg_emojis_msg (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_dialog_message_tops (
  id BIGINT NOT NULL PRIMARY KEY,
  dialog_id BIGINT NOT NULL,
  message_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME(3) NULL,
  UNIQUE KEY uk_dialog_msg_tops (dialog_id, message_id),
  KEY idx_dialog_msg_tops_dialog (dialog_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_dialog_message_todos (
  id BIGINT NOT NULL PRIMARY KEY,
  message_id BIGINT NOT NULL,
  dialog_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  remind_at DATETIME(3) NULL,
  done_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_dialog_msg_todos (message_id, user_id),
  KEY idx_dialog_msg_todos_user_id (user_id, done_at),
  KEY idx_dialog_msg_todos_remind (remind_at, done_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_dialog_message_translations (
  id BIGINT NOT NULL PRIMARY KEY,
  dialog_id BIGINT NOT NULL DEFAULT 0,
  message_id BIGINT NOT NULL DEFAULT 0,
  language VARCHAR(32) NOT NULL DEFAULT '',
  content LONGTEXT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_dialog_msg_translations (message_id, language),
  KEY idx_dialog_msg_translations_dialog (dialog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 会话级配置（群禁言等）；与 bluedock_dialog_users.is_muted（个人免打扰）区分
CREATE TABLE bluedock_dialog_configs (
  id BIGINT NOT NULL PRIMARY KEY,
  dialog_id BIGINT NOT NULL,
  is_chat_muted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_dialog_configs_dialog (dialog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对话侧 AI 多会话（与 assistant 的 bluedock_ai_assistant_sessions 并行）
CREATE TABLE bluedock_dialog_sessions (
  id BIGINT NOT NULL PRIMARY KEY,
  dialog_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  session_key VARCHAR(64) NOT NULL DEFAULT '',
  title VARCHAR(255) NOT NULL DEFAULT '',
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_dialog_sessions (dialog_id, user_id, session_key),
  KEY idx_dialog_sessions_user (dialog_id, user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_complaints (
  id BIGINT NOT NULL PRIMARY KEY,
  dialog_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  type INT NOT NULL DEFAULT 0,
  reason VARCHAR(500) NOT NULL DEFAULT '',
  images TEXT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  KEY idx_complaints_status (status, id),
  KEY idx_complaints_dialog (dialog_id),
  KEY idx_complaints_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_files (
  id BIGINT NOT NULL PRIMARY KEY,
  parent_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(255) NOT NULL DEFAULT '',
  type VARCHAR(32) NOT NULL DEFAULT 'file',
  extension VARCHAR(32) NULL DEFAULT '',
  size BIGINT NOT NULL DEFAULT 0,
  hash VARCHAR(64) NULL DEFAULT '',
  path VARCHAR(1024) NULL DEFAULT '',
  user_id BIGINT NOT NULL DEFAULT 0,
  created_user_id BIGINT NOT NULL DEFAULT 0,
  is_shared TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_files_user_id_parent (user_id, parent_id),
  KEY idx_files_hash_user_id (hash, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_file_contents (
  id BIGINT NOT NULL PRIMARY KEY,
  file_id BIGINT NOT NULL,
  content LONGTEXT NULL,
  text LONGTEXT NULL,
  size BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_file_contents_file_id (file_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_file_users (
  id BIGINT NOT NULL PRIMARY KEY,
  file_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  permission TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_file_users_file_user (file_id, user_id),
  KEY idx_file_users_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_file_links (
  id BIGINT NOT NULL PRIMARY KEY,
  file_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  permission TINYINT NOT NULL DEFAULT 0,
  allow_guest TINYINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_file_links_code (code),
  KEY idx_file_links_file_id (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_upload_objects (
  id BIGINT NOT NULL PRIMARY KEY,
  object_key VARCHAR(512) NOT NULL,
  url VARCHAR(2048) NOT NULL DEFAULT '',
  category VARCHAR(32) NOT NULL DEFAULT 'files',
  original_name VARCHAR(255) NOT NULL DEFAULT '',
  content_type VARCHAR(128) NOT NULL DEFAULT '',
  size_bytes BIGINT NOT NULL DEFAULT 0,
  provider VARCHAR(16) NOT NULL DEFAULT 'local',
  uploader_id BIGINT NULL,
  created_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_upload_object_key (object_key),
  KEY idx_upload_category_created (category, created_at),
  KEY idx_upload_uploader_created (uploader_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_reports (
  id BIGINT NOT NULL PRIMARY KEY,
  sign VARCHAR(64) NOT NULL DEFAULT '',
  title VARCHAR(255) NOT NULL DEFAULT '',
  type VARCHAR(16) NOT NULL DEFAULT 'daily',
  user_id BIGINT NOT NULL DEFAULT 0,
  content LONGTEXT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_reports_sign_type_user (sign, type, user_id),
  KEY idx_reports_user_id_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_report_receives (
  id BIGINT NOT NULL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  is_read TINYINT NOT NULL DEFAULT 0,
  receive_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_report_receives (report_id, user_id),
  KEY idx_report_receives_user_id_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_report_links (
  id BIGINT NOT NULL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  code VARCHAR(128) NOT NULL,
  open_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_report_links_code (code),
  UNIQUE KEY uk_report_links_report_user (report_id, user_id),
  KEY idx_report_links_report_id (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_report_ai_analyses (
  id BIGINT NOT NULL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  analysis_text LONGTEXT NULL,
  model VARCHAR(128) NOT NULL DEFAULT '',
  meta TEXT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_report_ai_analyses (report_id, user_id),
  KEY idx_report_ai_analyses_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_meetings (
  id BIGINT NOT NULL PRIMARY KEY,
  meeting_id VARCHAR(32) NOT NULL DEFAULT '',
  name VARCHAR(255) NOT NULL DEFAULT '',
  channel VARCHAR(128) NOT NULL DEFAULT '',
  user_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  end_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_meetings_meeting_id (meeting_id),
  KEY idx_meetings_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_meeting_messages (
  id BIGINT NOT NULL PRIMARY KEY,
  meeting_id VARCHAR(32) NOT NULL DEFAULT '',
  dialog_id BIGINT NOT NULL DEFAULT 0,
  message_id BIGINT NOT NULL DEFAULT 0,
  KEY idx_meeting_msgs_meeting_id (meeting_id),
  KEY idx_meeting_msgs_msg (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_search_docs (
  id BIGINT NOT NULL PRIMARY KEY,
  doc_type VARCHAR(32) NOT NULL DEFAULT '',
  ref_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  project_id BIGINT NOT NULL DEFAULT 0,
  title VARCHAR(500) NOT NULL DEFAULT '',
  content TEXT NULL,
  event_id VARCHAR(64) NULL DEFAULT '',
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_search_docs_type_ref (doc_type, ref_id),
  KEY idx_search_docs_title (title(191)),
  KEY idx_search_docs_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_ai_assistant_sessions (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  session_key VARCHAR(100) NOT NULL DEFAULT 'default',
  session_id VARCHAR(100) NOT NULL DEFAULT '',
  scene_key VARCHAR(100) NOT NULL DEFAULT '',
  title VARCHAR(255) NOT NULL DEFAULT '',
  data LONGTEXT NULL,
  images LONGTEXT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_ai_sessions (user_id, session_key, session_id),
  KEY idx_ai_sessions_user_id_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_ai_assistant_feedbacks (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  session_key VARCHAR(100) NOT NULL DEFAULT 'default',
  session_id VARCHAR(100) NOT NULL DEFAULT '',
  local_id BIGINT NOT NULL DEFAULT 0,
  feedback VARCHAR(16) NOT NULL DEFAULT '',
  prompt VARCHAR(1000) NULL,
  answer VARCHAR(2000) NULL,
  answer_digest VARCHAR(64) NULL,
  source_ids TEXT NULL,
  model VARCHAR(100) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_ai_feedback (user_id, session_key, session_id, local_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_ai_assistant_search_logs (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  dialog_id BIGINT NOT NULL DEFAULT 0,
  context_key VARCHAR(191) NOT NULL DEFAULT '',
  source VARCHAR(32) NOT NULL DEFAULT '',
  query_text VARCHAR(500) NOT NULL DEFAULT '',
  locale VARCHAR(8) NOT NULL DEFAULT '',
  source_ids TEXT NULL,
  top_score DECIMAL(8,6) NOT NULL DEFAULT 0,
  result_count INT NOT NULL DEFAULT 0,
  duration_ms INT NOT NULL DEFAULT 0,
  empty_hit TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NULL,
  KEY idx_ai_search_logs_user_id (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_app_badges (
  id BIGINT NOT NULL PRIMARY KEY,
  app_id VARCHAR(100) NOT NULL DEFAULT '',
  menu_key VARCHAR(100) NOT NULL DEFAULT '',
  user_id BIGINT NOT NULL DEFAULT 0,
  badge_count INT NOT NULL DEFAULT 0,
  badge_dot TINYINT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_app_badges (app_id, menu_key, user_id),
  KEY idx_app_badges_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_app_sorts (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  sorts TEXT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_app_sorts_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_tags (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  creator_user_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(20) NOT NULL DEFAULT '',
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  KEY idx_user_tags_user (user_id, deleted_at),
  KEY idx_user_tags_creator (creator_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_tag_recognitions (
  id BIGINT NOT NULL PRIMARY KEY,
  tag_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_tag_recognition (tag_id, user_id),
  KEY idx_tag_recognitions_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_installed_apps (
  id BIGINT NOT NULL PRIMARY KEY,
  app_id VARCHAR(100) NOT NULL DEFAULT '',
  name VARCHAR(255) NOT NULL DEFAULT '',
  secret VARCHAR(255) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'installed',
  version VARCHAR(32) NOT NULL DEFAULT '1.0.0',
  menus TEXT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_installed_apps_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_departments (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(64) NOT NULL DEFAULT '',
  parent_id BIGINT NOT NULL DEFAULT 0,
  owner_user_id BIGINT NOT NULL DEFAULT 0,
  dialog_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  KEY idx_user_departments_parent (parent_id),
  KEY idx_user_departments_owner (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_department_owners (
  id BIGINT NOT NULL PRIMARY KEY,
  department_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  UNIQUE KEY uk_dept_owners (department_id, user_id),
  KEY idx_dept_owners_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_department_members (
  id BIGINT NOT NULL PRIMARY KEY,
  department_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  UNIQUE KEY uk_dept_members (department_id, user_id),
  KEY idx_dept_members_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_favorites (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  fav_type VARCHAR(32) NOT NULL DEFAULT '',
  ref_id BIGINT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_favorites (user_id, fav_type, ref_id),
  KEY idx_user_favorites_user_id (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_bots (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  bot_id BIGINT NOT NULL DEFAULT 0,
  clear_day INT NOT NULL DEFAULT 90,
  clear_at DATETIME(3) NULL,
  webhook_url VARCHAR(1024) NULL,
  webhook_events TEXT NULL,
  webhook_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_bots_owner_bot (user_id, bot_id),
  KEY idx_user_bots_bot (bot_id),
  KEY idx_user_bots_clear_at (clear_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_task_browses (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  task_id BIGINT NOT NULL DEFAULT 0,
  browsed_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_task_browses (user_id, task_id),
  KEY idx_user_task_browses_browsed (user_id, browsed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_recent_items (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  target_type VARCHAR(50) NOT NULL DEFAULT '',
  target_id BIGINT NOT NULL DEFAULT 0,
  source_type VARCHAR(50) NOT NULL DEFAULT '',
  source_id BIGINT NOT NULL DEFAULT 0,
  browsed_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_recent_items (user_id, target_type, target_id, source_type, source_id),
  KEY idx_user_recent_browsed (user_id, browsed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_attendance_records (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  attendance_date DATE NOT NULL,
  times TEXT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_attendance_date (user_id, attendance_date),
  KEY idx_user_attendance_date (attendance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_attendance_macs (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  mac_address VARCHAR(64) NOT NULL DEFAULT '',
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_attendance_mac_address (user_id, mac_address),
  KEY idx_user_attendance_mac_address (mac_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_user_attendance_faces (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL DEFAULT 0,
  upload_object_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uk_user_attendance_face_user (user_id),
  KEY idx_user_attendance_face_upload (upload_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bluedock_auth_key_pairs (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  key_id VARCHAR(64) NOT NULL,
  public_key TEXT NOT NULL,
  private_key_enc TEXT NOT NULL COMMENT 'dev stores PEM; prod may encrypt at rest',
  algorithm VARCHAR(32) NOT NULL DEFAULT 'RSA-OAEP-SHA256',
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL,
  expired_at DATETIME(3) NULL,
  UNIQUE KEY uk_auth_key_pairs_key_id (key_id),
  KEY idx_auth_key_pairs_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
