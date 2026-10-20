-- Catálogo fixo de conquistas (igual ao que existia no app mockado).
-- Rode com: psql -U stepvolt -d stepvolt -f seed.sql
-- Seguro de rodar de novo (ON CONFLICT DO NOTHING).

INSERT INTO achievements_catalog (id, icon, name, description, category, rarity) VALUES
('s1', '👟', 'Primeiros Passos', 'Complete sua primeira atividade', 'Passos', 'comum'),
('s2', '🏃', 'Mil Passos', 'Dê 1.000 passos em uma sessão', 'Passos', 'comum'),
('s3', '🔥', '10K Day', '10.000 passos em um único dia', 'Passos', 'raro'),
('s4', '⚡', 'Ultra Maratonista', '100.000 passos no total', 'Passos', 'epico'),
('s5', '💎', 'Lenda dos Passos', '1.000.000 de passos no total', 'Passos', 'lendario'),
('e1', '🔋', 'Primeiro Watt', 'Gere energia pela primeira vez', 'Energia', 'comum'),
('e2', '⚡', '100 mWh', 'Gere 100 mWh no total', 'Energia', 'comum'),
('e3', '🌩️', 'Megawatt', 'Gere 1.000 mWh em um único dia', 'Energia', 'epico'),
('e4', '☀️', 'Central Elétrica', 'Gere 100.000 mWh no total', 'Energia', 'lendario'),
('a1', '📅', 'Semana Ativa', '7 dias consecutivos de atividade', 'Atividade', 'raro'),
('a2', '🗓️', 'Mês Dedicado', '30 dias consecutivos de atividade', 'Atividade', 'epico'),
('a3', '💯', 'Centenário', '100 atividades registradas', 'Atividade', 'lendario'),
('m1', '🎯', 'Primeira Meta', 'Atinja sua primeira meta', 'Metas', 'comum'),
('m2', '🏆', '10 Metas', 'Atinja 10 metas no total', 'Metas', 'raro'),
('m3', '👑', 'Campeão de Metas', 'Atinja 100 metas no total', 'Metas', 'lendario'),
('so1', '👥', 'Primeiro Seguidor', 'Ganhe seu primeiro seguidor', 'Social', 'comum'),
('so2', '❤️', '10 Curtidas', 'Receba 10 curtidas nos seus posts', 'Social', 'comum'),
('so3', '🌟', 'Influencer', 'Alcance 1.000 seguidores', 'Social', 'epico')
ON CONFLICT (id) DO NOTHING;
