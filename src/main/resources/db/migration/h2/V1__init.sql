CREATE TABLE team (
  id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  name   VARCHAR(120) NOT NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE app_user (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  username             VARCHAR(80)  NOT NULL UNIQUE,
  password_hash        VARCHAR(100) NOT NULL,
  full_name            VARCHAR(150) NOT NULL,
  team_id              BIGINT NOT NULL,
  role                 VARCHAR(20)  NOT NULL,
  auth_source          VARCHAR(10)  NOT NULL DEFAULT 'LOCAL',
  active               BOOLEAN NOT NULL DEFAULT TRUE,
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_user_team FOREIGN KEY (team_id) REFERENCES team(id)
);

CREATE TABLE reason (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  title           VARCHAR(200) NOT NULL,
  sort_order      INT NOT NULL DEFAULT 0,
  requires_detail BOOLEAN NOT NULL DEFAULT FALSE,
  active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE report (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  tracking_no          VARCHAR(20) UNIQUE,
  caller_user_id       BIGINT NOT NULL,
  caller_team_id       BIGINT NOT NULL,
  target_team_id       BIGINT NOT NULL,
  target_person        VARCHAR(150),
  contact_method       VARCHAR(20) NOT NULL,
  contact_at           TIMESTAMP NOT NULL,
  attempts_count       INT NOT NULL DEFAULT 1,
  subject              VARCHAR(300) NOT NULL,
  reason_id            BIGINT NOT NULL,
  reason_detail        VARCHAR(2000),
  status               VARCHAR(20) NOT NULL,
  submitted_by_manager BOOLEAN NOT NULL DEFAULT FALSE,
  created_at           TIMESTAMP NOT NULL,
  updated_at           TIMESTAMP NOT NULL,
  CONSTRAINT fk_rep_caller FOREIGN KEY (caller_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_rep_cteam  FOREIGN KEY (caller_team_id) REFERENCES team(id),
  CONSTRAINT fk_rep_tteam  FOREIGN KEY (target_team_id) REFERENCES team(id),
  CONSTRAINT fk_rep_reason FOREIGN KEY (reason_id)      REFERENCES reason(id)
);
CREATE INDEX idx_report_status ON report(status);
CREATE INDEX idx_report_cteam  ON report(caller_team_id);
CREATE INDEX idx_report_tteam  ON report(target_team_id);

CREATE TABLE report_event (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  report_id     BIGINT NOT NULL,
  actor_user_id BIGINT NOT NULL,
  event_type    VARCHAR(30) NOT NULL,
  from_status   VARCHAR(20),
  to_status     VARCHAR(20),
  note_text     VARCHAR(2000),
  changes_text  VARCHAR(4000),
  created_at    TIMESTAMP NOT NULL,
  CONSTRAINT fk_ev_report FOREIGN KEY (report_id)     REFERENCES report(id),
  CONSTRAINT fk_ev_actor  FOREIGN KEY (actor_user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_event_report ON report_event(report_id);

INSERT INTO reason (title, sort_order, requires_detail) VALUES
 ('پاسخ نداد', 1, FALSE),
 ('مشغول بود', 2, FALSE),
 ('خارج از ساعت کاری', 3, FALSE),
 ('مسیر ارتباطی اشتباه (شماره / آدرس نادرست)', 4, FALSE),
 ('پاسخ داد ولی پیگیری نشد', 5, FALSE),
 ('به تیم دیگری ارجاع داد', 6, FALSE),
 ('سایر', 7, TRUE);
