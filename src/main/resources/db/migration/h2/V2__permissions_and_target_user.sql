-- شخص هدف از این پس از لیست کاربران انتخاب می‌شود (ستون target_person به‌عنوان نسخه‌ی متنی نام باقی می‌ماند)
ALTER TABLE report ADD COLUMN target_user_id BIGINT;
ALTER TABLE report ADD CONSTRAINT fk_rep_tuser FOREIGN KEY (target_user_id) REFERENCES app_user(id);

-- دسترسی‌های اضافه / سلب‌شده‌ی هر کاربر نسبت به پیش‌فرض نقش
CREATE TABLE user_perm_grant (
  user_id    BIGINT      NOT NULL,
  permission VARCHAR(30) NOT NULL,
  PRIMARY KEY (user_id, permission),
  CONSTRAINT fk_upg_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE TABLE user_perm_revoke (
  user_id    BIGINT      NOT NULL,
  permission VARCHAR(30) NOT NULL,
  PRIMARY KEY (user_id, permission),
  CONSTRAINT fk_upr_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
