const express = require('express');
const { pool } = require('../db/pool');
const { requireAuth } = require('../middleware/auth');
const { evaluateAchievements } = require('../services/gamification');

const router = express.Router();

router.get('/', requireAuth, async (req, res) => {
  try {
    await evaluateAchievements(req.userId); // garante que o estado está em dia
    const result = await pool.query(
      `SELECT c.*, COALESCE(ua.unlocked, false) AS unlocked, ua.unlocked_date
       FROM achievements_catalog c
       LEFT JOIN user_achievements ua ON ua.achievement_id = c.id AND ua.user_id = $1
       ORDER BY c.category, c.id`,
      [req.userId]
    );
    res.json(
      result.rows.map((a) => ({
        id: a.id,
        icon: a.icon,
        name: a.name,
        desc: a.description,
        unlocked: a.unlocked,
        date: a.unlocked_date,
        rarity: a.rarity.toUpperCase(),
        category: a.category,
      }))
    );
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar conquistas.' });
  }
});

module.exports = router;
