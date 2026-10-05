-- نقش‌ها از enum ثابت به جدول قابل‌مدیریت منتقل می‌شوند؛ هر کاربر می‌تواند چند نقش داشته باشد
CREATE TABLE app_role (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(40) UNIQUE,
  name        VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(300),
  system_role BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE app_role_permission (
  role_id    BIGINT      NOT NULL,
  permission VARCHAR(30) NOT NULL,
  PRIMARY KEY (role_id, permission),
  CONSTRAINT fk_arp_role FOREIGN KEY (role_id) REFERENCES app_role(id)
);
CREATE TABLE user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES app_role(id)
);

INSERT INTO app_role (code, name, description, system_role) VALUES
 ('USER', 'عضو تیم', 'ثبت گزارش و دیدن گزارش‌های تیم خودش', TRUE),
 ('TEAM_MANAGER', 'مدیر تیم', 'تأیید گزارش‌های تیم، آمار تیم و خروجی Excel', TRUE),
 ('SENIOR_MANAGER', 'مدیرعامل / مدیریت ارشد', 'داشبورد و دیدن همه‌ی گزارش‌های تأییدشده، بررسی و بستن', TRUE),
 ('ADMIN', 'ادمین', 'مدیریت سیستم، کاربران، نقش‌ها و تنظیمات', TRUE);

INSERT INTO app_role_permission (role_id, permission) SELECT id, 'REPORT_CREATE' FROM app_role WHERE code = 'USER';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'REPORT_CREATE' FROM app_role WHERE code = 'TEAM_MANAGER';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'TEAM_APPROVE' FROM app_role WHERE code = 'TEAM_MANAGER';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'TEAM_STATS' FROM app_role WHERE code = 'TEAM_MANAGER';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'EXPORT' FROM app_role WHERE code = 'TEAM_MANAGER';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'VIEW_ALL' FROM app_role WHERE code = 'SENIOR_MANAGER';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'REVIEW_CLOSE' FROM app_role WHERE code = 'SENIOR_MANAGER';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'EXPORT' FROM app_role WHERE code = 'SENIOR_MANAGER';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'ADMIN_PANEL' FROM app_role WHERE code = 'ADMIN';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'VIEW_ALL' FROM app_role WHERE code = 'ADMIN';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'VIEW_EVERYTHING' FROM app_role WHERE code = 'ADMIN';
INSERT INTO app_role_permission (role_id, permission) SELECT id, 'EXPORT' FROM app_role WHERE code = 'ADMIN';

INSERT INTO user_role (user_id, role_id) SELECT u.id, r.id FROM app_user u JOIN app_role r ON r.code = u.role;
ALTER TABLE app_user DROP COLUMN role;

CREATE TABLE app_setting (
  setting_key   VARCHAR(80)  PRIMARY KEY,
  setting_value VARCHAR(500) NOT NULL
);
INSERT INTO app_setting (setting_key, setting_value) VALUES ('dashboard.refresh.seconds', '60');
