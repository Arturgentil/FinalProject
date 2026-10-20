const express = require('express');
const { pool } = require('../db/pool');
const { requireAuth } = require('../middleware/auth');
const { validateChallenge, METRICS } = require('../utils/validate');
const { rateLimit } = require('../middleware/rateLimit');

const router = express.Router();
const leaderboardRouter = express.Router();

const createLimit = rateLimit({
  windowMs: 60 * 60_000, max: 10, key: (req) => `c|${req.userId || req.ip}`,
  message: 'Você criou muitos desafios seguidos. Tente mais tarde.',
});

// Expressão SQL que soma a medida do desafio a partir das atividades da pessoa
// dentro do período dele. O app não calcula nada: tudo sai das atividades
// que o servidor já guarda.
function metricSum(metric) {
  if (metric === 'energy') return 'COALESCE(SUM(a.energy), 0)';
  if (metric === 'distance') return 'COALESCE(SUM(a.distance_km), 0)';
  return 'COALESCE(SUM(a.steps), 0)';
}

function metricColumn(metric) {
  return metric === 'energy' ? 'energy' : metric === 'distance' ? 'distance_km' : 'steps';
}

function serialize(row) {
  const finished = new Date(row.ends_at) < new Date();
  return {
    id: String(row.id),
    title: row.title,
    metric: row.metric,
    targetValue: Number(row.target_value) || 0,
    startsAt: row.starts_at,
    endsAt: row.ends_at,
    participants: Number(row.participants) || 0,
    joined: !!row.joined,
    mine: row.owner_id === row.me,
    ownerName: row.owner_name,
    myValue: Number(row.my_value) || 0,
    status: finished ? 'finished' : 'active',
  };
}

// Quem pode ver/entrar: o dono, quem já participa e quem segue o dono.
const VISIBLE = `(
  c.owner_id = $1
  OR EXISTS (SELECT 1 FROM challenge_participants p WHERE p.challenge_id = c.id AND p.user_id = $1)
  OR EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = $1 AND f.followee_id = c.owner_id)
)`;

async function fetchOne(id, userId) {
  const r = await pool.query(
    `SELECT c.*, $1::int AS me, u.name AS owner_name,
            (SELECT COUNT(*)::int FROM challenge_participants p WHERE p.challenge_id = c.id) AS participants,
            EXISTS (SELECT 1 FROM challenge_participants p WHERE p.challenge_id = c.id AND p.user_id = $1) AS joined,
            CASE c.metric
              WHEN 'energy' THEN (SELECT COALESCE(SUM(a.energy),0) FROM activities a WHERE a.user_id = $1 AND a.created_at BETWEEN c.starts_at AND c.ends_at)
              WHEN 'distance' THEN (SELECT COALESCE(SUM(a.distance_km),0) FROM activities a WHERE a.user_id = $1 AND a.created_at BETWEEN c.starts_at AND c.ends_at)
              ELSE (SELECT COALESCE(SUM(a.steps),0) FROM activities a WHERE a.user_id = $1 AND a.created_at BETWEEN c.starts_at AND c.ends_at)
            END AS my_value
     FROM challenges c JOIN users u ON u.id = c.owner_id
     WHERE c.id = $2 AND ${VISIBLE}`,
    [userId, id]
  );
  return r.rows[0] || null;
}

function validId(v) {
  return /^[0-9]{1,9}$/.test(String(v));
}

router.get('/', requireAuth, async (req, res) => {
  try {
    const r = await pool.query(
      `SELECT c.*, $1::int AS me, u.name AS owner_name,
              (SELECT COUNT(*)::int FROM challenge_participants p WHERE p.challenge_id = c.id) AS participants,
              EXISTS (SELECT 1 FROM challenge_participants p WHERE p.challenge_id = c.id AND p.user_id = $1) AS joined,
              CASE c.metric
                WHEN 'energy' THEN (SELECT COALESCE(SUM(a.energy),0) FROM activities a WHERE a.user_id = $1 AND a.created_at BETWEEN c.starts_at AND c.ends_at)
                WHEN 'distance' THEN (SELECT COALESCE(SUM(a.distance_km),0) FROM activities a WHERE a.user_id = $1 AND a.created_at BETWEEN c.starts_at AND c.ends_at)
                ELSE (SELECT COALESCE(SUM(a.steps),0) FROM activities a WHERE a.user_id = $1 AND a.created_at BETWEEN c.starts_at AND c.ends_at)
              END AS my_value
       FROM challenges c JOIN users u ON u.id = c.owner_id
       WHERE ${VISIBLE} AND c.ends_at > now() - interval '14 days'
       ORDER BY (c.ends_at > now()) DESC, c.ends_at ASC
       LIMIT 50`,
      [req.userId]
    );
    res.json(r.rows.map(serialize));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar desafios.' });
  }
});

router.post('/', requireAuth, createLimit, async (req, res) => {
  const body = req.body || {};
  const problem = validateChallenge(body);
  if (problem) return res.status(400).json({ error: problem });
  try {
    const open = await pool.query(
      'SELECT COUNT(*)::int AS n FROM challenges WHERE owner_id = $1 AND ends_at > now()', [req.userId]);
    if (open.rows[0].n >= 10) {
      return res.status(400).json({ error: 'Você já tem 10 desafios em andamento. Espere algum terminar.' });
    }
    const days = Number(body.days);
    const ins = await pool.query(
      `INSERT INTO challenges (owner_id, title, metric, target_value, starts_at, ends_at)
       VALUES ($1,$2,$3,$4, now(), now() + ($5 || ' days')::interval) RETURNING id`,
      [req.userId, body.title.trim(), body.metric, Number(body.targetValue) || 0, String(days)]
    );
    await pool.query('INSERT INTO challenge_participants (challenge_id, user_id) VALUES ($1,$2)', [ins.rows[0].id, req.userId]);
    res.status(201).json(serialize(await fetchOne(ins.rows[0].id, req.userId)));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao criar o desafio.' });
  }
});

router.post('/:id/join', requireAuth, async (req, res) => {
  if (!validId(req.params.id)) return res.status(404).json({ error: 'Desafio não encontrado.' });
  try {
    const ch = await fetchOne(req.params.id, req.userId);
    if (!ch) return res.status(404).json({ error: 'Desafio não encontrado.' });
    if (new Date(ch.ends_at) < new Date()) return res.status(400).json({ error: 'Este desafio já terminou.' });
    await pool.query(
      'INSERT INTO challenge_participants (challenge_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING',
      [ch.id, req.userId]
    );
    res.json(serialize(await fetchOne(ch.id, req.userId)));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao entrar no desafio.' });
  }
});

router.post('/:id/leave', requireAuth, async (req, res) => {
  if (!validId(req.params.id)) return res.status(404).json({ error: 'Desafio não encontrado.' });
  try {
    const ch = await fetchOne(req.params.id, req.userId);
    if (!ch) return res.status(404).json({ error: 'Desafio não encontrado.' });
    if (ch.owner_id === req.userId) {
      return res.status(400).json({ error: 'Quem criou o desafio não pode sair dele.' });
    }
    await pool.query('DELETE FROM challenge_participants WHERE challenge_id = $1 AND user_id = $2', [ch.id, req.userId]);
    res.json({ ok: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao sair do desafio.' });
  }
});

router.get('/:id/leaderboard', requireAuth, async (req, res) => {
  if (!validId(req.params.id)) return res.status(404).json({ error: 'Desafio não encontrado.' });
  try {
    const ch = await fetchOne(req.params.id, req.userId);
    if (!ch) return res.status(404).json({ error: 'Desafio não encontrado.' });
    const r = await pool.query(
      `SELECT u.id, u.name, u.username, u.avatar_url, ${metricSum(ch.metric)} AS value
       FROM challenge_participants p
       JOIN users u ON u.id = p.user_id
       LEFT JOIN activities a ON a.user_id = u.id AND a.created_at BETWEEN $2 AND $3
       WHERE p.challenge_id = $1
       GROUP BY u.id
       ORDER BY value DESC, u.name ASC
       LIMIT 100`,
      [ch.id, ch.starts_at, ch.ends_at]
    );
    res.json(rank(r.rows, req.userId));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar o ranking do desafio.' });
  }
});

function rank(rows, me) {
  let pos = 0, prev = null, shown = 0;
  return rows.map((row) => {
    shown++;
    const v = Number(row.value) || 0;
    if (v !== prev) { pos = shown; prev = v; }
    return {
      rank: pos,
      userId: String(row.id),
      name: row.name,
      username: row.username,
      avatarUrl: row.avatar_url || '',
      value: v,
      isMe: row.id === me,
    };
  });
}

// Ranking entre você e quem você segue, nos últimos 7 ou 30 dias.
leaderboardRouter.get('/', requireAuth, async (req, res) => {
  const days = req.query.period === 'month' ? 30 : 7;
  const metric = METRICS.includes(req.query.metric) ? req.query.metric : 'energy';
  const col = metricColumn(metric);
  try {
    const r = await pool.query(
      `SELECT u.id, u.name, u.username, u.avatar_url, COALESCE(SUM(a.${col}), 0) AS value
       FROM users u
       LEFT JOIN activities a ON a.user_id = u.id AND a.created_at > now() - ($2 || ' days')::interval
       WHERE u.id = $1 OR u.id IN (SELECT followee_id FROM follows WHERE follower_id = $1)
       GROUP BY u.id
       ORDER BY value DESC, u.name ASC
       LIMIT 100`,
      [req.userId, String(days)]
    );
    res.json(rank(r.rows, req.userId));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar o ranking.' });
  }
});

module.exports = { challengesRouter: router, leaderboardRouter };
