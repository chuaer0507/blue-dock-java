ALTER TABLE bluedock_dialog_messages
  ADD COLUMN session_key VARCHAR(100) NOT NULL DEFAULT '' AFTER tag_user_id,
  ADD KEY idx_dialog_msgs_session (dialog_id, session_key, id);
