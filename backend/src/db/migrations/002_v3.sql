-- StepVolt - migração V3
-- Acrescenta o que a reformulação do app precisa no servidor: dispositivos
-- pareados, amostras de telemetria em lote e avisos gerados pelos limites de
-- saúde. Nada aqui substitui as tabelas existentes; o app continua enviando
-- atividades pela rota antiga.
--
-- Rode com: node src/db/migrate.js (o migrate.js aplica schema.sql, esta
-- migração e o seed, nessa ordem) ou psql -f db/migrations/002_v3.sql

CREATE TABLE IF NOT EXISTS devices (
  id SERIAL PRIMARY KEY,
  user_id INT REFERENCES users(id) ON DELETE CASCADE,
  address VARCHAR(40) NOT NULL,
  name VARCHAR(80) DEFAULT '',
  firmware_version VARCHAR(30) DEFAULT '',
  last_seen TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (user_id, address)
);

-- Amostras cruas, em lote. É uma tabela "alta e magra" (uma linha por
-- leitura de uma grandeza) para caber qualquer sensor novo sem migração —
-- o mesmo formato usado no banco local do app (app/.../data/LocalDb.java).
CREATE TABLE IF NOT EXISTS telemetry_samples (
  id BIGSERIAL PRIMARY KEY,
  user_id INT REFERENCES users(id) ON DELETE CASCADE,
  device_id INT REFERENCES devices(id) ON DELETE SET NULL,
  ts BIGINT NOT NULL,          -- epoch ms, igual ao que o app grava localmente
  metric VARCHAR(30) NOT NULL,
  value DOUBLE PRECISION NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_telemetry_user_metric_ts
  ON telemetry_samples (user_id, metric, ts);

CREATE TABLE IF NOT EXISTS alerts (
  id BIGSERIAL PRIMARY KEY,
  user_id INT REFERENCES users(id) ON DELETE CASCADE,
  ts BIGINT NOT NULL,
  metric VARCHAR(30) NOT NULL,
  value DOUBLE PRECISION,
  threshold DOUBLE PRECISION,
  severity SMALLINT DEFAULT 0,   -- 0 info, 1 atenção, 2 crítico — mesma escala do app
  message TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alerts_user_ts ON alerts (user_id, ts DESC);
