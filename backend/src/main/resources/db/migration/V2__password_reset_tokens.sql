-- Password reset links. Only a SHA-256 hash of the token is stored, never the token itself.
CREATE TABLE password_reset_tokens (
  token_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expiry_date TIMESTAMP NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_reset_tokens_user ON password_reset_tokens(user_id);
