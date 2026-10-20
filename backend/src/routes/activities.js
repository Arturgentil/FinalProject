const express = require('express');
const { pool } = require('../db/pool');
const { requireAuth } = require('../middleware/auth');
const { updateGoalsForActivity, evaluateAchievements } = require('../services/gamification');
const { validateActivity } = require('../utils/validate');
const { shareActivity } = require('../services/social');

const router = express.Router();

function serializeActivity(a) {
  return {
    id: String(a.id),
    date: a.activity_date,
    time: a.start_time,
    endTime: a.end_time,
    steps: Number(a.steps),
    energy: Number(a.energy),
    duration: a.duration_min,
    distance: Number(a.distance_km),
    bpmAvg: a.bpm_avg,
    bpmSamples: a.bpm_samples || [],
    spo2Samples: a.spo2_samples || [],
    shared: a.shared,
    kind: a.kind || 'walk',
    elevationGain: Number(a.elevation_gain_m) || 0,
    updatedAt: a.updated_at,
    createdAt: a.created_at,
  };
}

// Lista paginada. Sem parâmetros devolve até 200 (como antes, para apps antigos).
//   ?limit=1..200   tamanho da página
//   ?offset=0..     deslocamento
//   ?since=ISO8601  só o que foi criado/alterado depois (sincronização incremental)
// O total (sem paginação) vem no cabeçalho X-Total-Count.
router.get('/', requireAuth, async (req, res) => {
  const limit = Math.min(200, Math.max(1, parseInt(req.query.limit, 10) || 200));
  const offset = Math.max(0, parseInt(req.query.offset, 10) || 0);
  let since = null;
  if (req.query.since) {
    since = new Date(req.query.since);
    if (Number.isNaN(since.getTime())) return res.status(400).json({ error: 'since inválido (use ISO 8601).' });
  }
  try {
    const where = since ? 'user_id = $1 AND updated_at > $2' : 'user_id = $1';
    const params = since ? [req.userId, since] : [req.userId];
    const total = await pool.query(`SELECT COUNT(*)::int AS n FROM activities WHERE ${where}`, params);
    const result = await pool.query(
      `SELECT * FROM activities WHERE ${where} ORDER BY created_at DESC, id DESC LIMIT ${limit} OFFSET ${offset}`,
      params
    );
    res.set('X-Total-Count', String(total.rows[0].n));
    res.json(result.rows.map(serializeActivity));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar histórico.' });
  }
});

router.get('/:id', requireAuth, async (req, res) => {
  if (!/^[0-9]{1,9}$/.test(req.params.id)) return res.status(404).json({ error: 'Atividade não encontrada.' });
  try {
    const result = await pool.query('SELECT * FROM activities WHERE id = $1 AND user_id = $2', [req.params.id, req.userId]);
    if (result.rows.length === 0) return res.status(404).json({ error: 'Atividade não encontrada.' });
    res.json(serializeActivity(result.rows[0]));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar atividade.' });
  }
});

// Chamado pelo app quando o usuário aperta "Pausar atividade" no Dashboard
// depois de ter registrado passos/energia reais vindos do ESP32 via BLE.
router.post('/', requireAuth, async (req, res) => {
  const { date, time, endTime, steps, energy, duration, distance, bpmAvg, bpmSamples, spo2Samples, kind, elevationGain } = req.body || {};
  if (steps == null || energy == null) {
    return res.status(400).json({ error: 'steps e energy são obrigatórios.' });
  }
  const problem = validateActivity(req.body);
  if (problem) return res.status(400).json({ error: problem });
  try {
    const result = await pool.query(
      `INSERT INTO activities (user_id, activity_date, start_time, end_time, steps, energy, duration_min, distance_km, bpm_avg, bpm_samples, spo2_samples, kind, elevation_gain_m)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13) RETURNING *`,
      [req.userId, date || new Date().toISOString().slice(0, 10), time || null, endTime || null, Math.round(steps), energy, Math.round(duration || 0), distance || 0, Math.round(bpmAvg || 0),
       (bpmSamples || []).map(Math.round), (spo2Samples || []).map(Math.round), kind || 'walk', elevationGain || 0]
    );
    const activity = result.rows[0];

    await pool.query(
      `UPDATE users SET
         total_steps = total_steps + $1,
         total_energy = total_energy + $2,
         record_steps = GREATEST(record_steps, $1),
         record_energy = GREATEST(record_energy, $2)
       WHERE id = $3`,
      [steps, energy, req.userId]
    );

    await updateGoalsForActivity(req.userId, { steps, energy });
    await evaluateAchievements(req.userId);

    // Compartilhamento automático (Ajustes > Privacidade): publica no feed sem pedir.
    const pref = await pool.query('SELECT auto_share FROM users WHERE id = $1', [req.userId]);
    if (pref.rows[0] && pref.rows[0].auto_share) await shareActivity(req.userId, activity);

    res.status(201).json(serializeActivity(activity));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao salvar atividade.' });
  }
});

router.put('/:id/share', requireAuth, async (req, res) => {
  if (!/^[0-9]{1,9}$/.test(req.params.id)) return res.status(404).json({ error: 'Atividade não encontrada.' });
  try {
    const activityRes = await pool.query('SELECT * FROM activities WHERE id = $1 AND user_id = $2', [req.params.id, req.userId]);
    if (activityRes.rows.length === 0) return res.status(404).json({ error: 'Atividade não encontrada.' });
    const activity = activityRes.rows[0];

    await shareActivity(req.userId, activity);
    res.json(serializeActivity(activity));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao compartilhar atividade.' });
  }
});

module.exports = router;
