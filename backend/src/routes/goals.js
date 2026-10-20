const express = require('express');
const { pool } = require('../db/pool');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

function serializeGoal(g) {
  return {
    id: String(g.id),
    type: g.type.toUpperCase(),
    target: Number(g.target),
    current: Number(g.current),
    period: g.period.toUpperCase(),
    completed: g.completed,
    completedDate: g.completed_date,
  };
}

router.get('/', requireAuth, async (req, res) => {
  try {
    const activeRes = await pool.query(
      'SELECT * FROM goals WHERE user_id = $1 AND completed = false ORDER BY created_at DESC',
      [req.userId]
    );
    const completedRes = await pool.query(
      'SELECT * FROM goals WHERE user_id = $1 AND completed = true ORDER BY created_at DESC LIMIT 20',
      [req.userId]
    );
    res.json({
      active: activeRes.rows.map(serializeGoal),
      completed: completedRes.rows.map(serializeGoal),
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar metas.' });
  }
});

router.post('/', requireAuth, async (req, res) => {
  const { type, target, period } = req.body;
  if (!type || !target || !period) {
    return res.status(400).json({ error: 'Preencha tipo, valor alvo e período.' });
  }
  try {
    const result = await pool.query(
      `INSERT INTO goals (user_id, type, target, current, period)
       VALUES ($1,$2,$3,0,$4) RETURNING *`,
      [req.userId, String(type).toLowerCase(), target, String(period).toLowerCase()]
    );
    res.status(201).json(serializeGoal(result.rows[0]));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao criar meta.' });
  }
});

module.exports = router;
