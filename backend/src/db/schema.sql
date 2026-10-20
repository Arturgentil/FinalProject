-- StepVolt - schema PostgreSQL
-- Rode com: psql -U stepvolt -d stepvolt -f schema.sql

CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  username VARCHAR(60) UNIQUE NOT NULL,
  email VARCHAR(160) UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  birth_date VARCHAR(20) DEFAULT '',
  weight NUMERIC(5,1) DEFAULT 0,
  height NUMERIC(5,1) DEFAULT 0,
  bio TEXT DEFAULT '',
  avatar_url TEXT DEFAULT '',
  shoe_upper VARCHAR(9) DEFAULT '#00F5A0',
  shoe_sole VARCHAR(9) DEFAULT '#1A1A2E',
  shoe_laces VARCHAR(9) DEFAULT '#FFFFFF',
  shoe_details VARCHAR(9) DEFAULT '#7C3AFF',
  shoe_theme VARCHAR(20) DEFAULT 'neon',
  total_steps BIGINT DEFAULT 0,
  total_energy NUMERIC(12,2) DEFAULT 0,
  record_steps BIGINT DEFAULT 0,
  record_energy NUMERIC(12,2) DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS follows (
  follower_id INT REFERENCES users(id) ON DELETE CASCADE,
  followee_id INT REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (follower_id, followee_id)
);

CREATE TABLE IF NOT EXISTS activities (
  id SERIAL PRIMARY KEY,
  user_id INT REFERENCES users(id) ON DELETE CASCADE,
  activity_date VARCHAR(20) NOT NULL,
  start_time VARCHAR(10),
  end_time VARCHAR(10),
  steps BIGINT DEFAULT 0,
  energy NUMERIC(12,2) DEFAULT 0,
  duration_min INT DEFAULT 0,
  distance_km NUMERIC(6,2) DEFAULT 0,
  bpm_avg INT DEFAULT 0,
  bpm_samples INTEGER[] DEFAULT '{}',
  spo2_samples INTEGER[] DEFAULT '{}',
  shared BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS posts (
  id SERIAL PRIMARY KEY,
  user_id INT REFERENCES users(id) ON DELETE CASCADE,
  activity_id INT REFERENCES activities(id) ON DELETE SET NULL,
  steps BIGINT DEFAULT 0,
  energy NUMERIC(12,2) DEFAULT 0,
  duration_min INT DEFAULT 0,
  shoe_color VARCHAR(9) DEFAULT '#00F5A0',
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS post_likes (
  post_id INT REFERENCES posts(id) ON DELETE CASCADE,
  user_id INT REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS comments (
  id SERIAL PRIMARY KEY,
  post_id INT REFERENCES posts(id) ON DELETE CASCADE,
  user_id INT REFERENCES users(id) ON DELETE CASCADE,
  text TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS goals (
  id SERIAL PRIMARY KEY,
  user_id INT REFERENCES users(id) ON DELETE CASCADE,
  type VARCHAR(20) NOT NULL,      -- passos | energia | batimentos | oxigenacao
  target NUMERIC(12,2) NOT NULL,
  current NUMERIC(12,2) DEFAULT 0,
  period VARCHAR(10) NOT NULL,    -- diario | semanal | mensal
  completed BOOLEAN DEFAULT false,
  completed_date VARCHAR(20),
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS achievements_catalog (
  id VARCHAR(10) PRIMARY KEY,
  icon VARCHAR(10),
  name VARCHAR(80),
  description VARCHAR(200),
  category VARCHAR(30),
  rarity VARCHAR(12)              -- comum | raro | epico | lendario
);

CREATE TABLE IF NOT EXISTS user_achievements (
  user_id INT REFERENCES users(id) ON DELETE CASCADE,
  achievement_id VARCHAR(10) REFERENCES achievements_catalog(id) ON DELETE CASCADE,
  unlocked BOOLEAN DEFAULT false,
  unlocked_date VARCHAR(20),
  PRIMARY KEY (user_id, achievement_id)
);

CREATE TABLE IF NOT EXISTS notifications (
  id SERIAL PRIMARY KEY,
  user_id INT REFERENCES users(id) ON DELETE CASCADE,
  type VARCHAR(20) NOT NULL,      -- like | comment | goal | record | health | follow
  message TEXT NOT NULL,
  actor_avatar_url TEXT,
  read BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_activities_user ON activities(user_id);
CREATE INDEX IF NOT EXISTS idx_posts_user ON posts(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_goals_user ON goals(user_id);
