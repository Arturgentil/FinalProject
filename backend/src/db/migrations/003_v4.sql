-- StepVolt - migração V4
-- Renovação de token (refresh), confirmação de e-mail, login com Google,
-- sincronização incremental de atividades e desafios entre pessoas.
-- Seguro para rodar mais de uma vez.

-- ---- Contas -----------------------------------------------------------
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT true;
-- Contas que já existiam ficam como confirmadas (não vamos cobrar quem já usa);
-- contas novas nascem não confirmadas.
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS email_verifications (
  user_id INT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL
);

-- ---- Atividades: sincronização incremental e novos campos -------------
ALTER TABLE activities ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE activities ADD COLUMN IF NOT EXISTS kind VARCHAR(10) DEFAULT 'walk';
ALTER TABLE activities ADD COLUMN IF NOT EXISTS elevation_gain_m NUMERIC(8,1) DEFAULT 0;
UPDATE activities SET updated_at = created_at WHERE updated_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_activities_user_updated ON activities (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_activities_user_created ON activities (user_id, created_at DESC);

-- ---- Desafios ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS challenges (
  id SERIAL PRIMARY KEY,
  owner_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(60) NOT NULL,
  metric VARCHAR(10) NOT NULL CHECK (metric IN ('steps','energy','distance')),
  target_value NUMERIC(14,2) DEFAULT 0,
  starts_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ends_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_challenges_owner ON challenges (owner_id);
CREATE INDEX IF NOT EXISTS idx_challenges_ends ON challenges (ends_at);

CREATE TABLE IF NOT EXISTS challenge_participants (
  challenge_id INT NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
  user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  joined_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (challenge_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_participants_user ON challenge_participants (user_id);
