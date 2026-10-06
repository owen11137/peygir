-- Read-only supervision does not change a user's primary team or workflow permissions.
CREATE TABLE user_team_view (
  user_id BIGINT NOT NULL,
  team_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, team_id),
  CONSTRAINT fk_view_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_view_team FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
);
CREATE INDEX idx_team_view_team ON user_team_view(team_id);
