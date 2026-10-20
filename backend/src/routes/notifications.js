const express = require('express');
const { pool } = require('../db/pool');
const { requireAuth } = require('../middleware/auth');
const { timeAgo } = require('../utils/timeAgo');

const router = express.Router();

router.get('/', requireAuth, async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT * FROM notifications WHERE user_id = $1 ORDER BY created_at DESC LIMIT 100',
      [req.userId]
    );
    res.json(
      result.rows.map((n) => ({
        id: String(n.id),
        type: n.type.toUpperCase(),
        message: n.message,
        time: timeAgo(n.created_at),
        avatarUrl: n.actor_avatar_url || null,
        read: n.read,
      }))
    );
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar notificações.' });
  }
});

router.put('/read-all', requireAuth, async (req, res) => {
  try {
    await pool.query('UPDATE notifications SET read = true WHERE user_id = $1', [req.userId]);
    res.json({ ok: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao marcar como lidas.' });
  }
});

router.put('/:id/read', requireAuth, async (req, res) => {
  try {
    await pool.query('UPDATE notifications SET read = true WHERE id = $1 AND user_id = $2', [req.params.id, req.userId]);
    res.json({ ok: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao marcar como lida.' });
  }
});

router.delete('/:id', requireAuth, async (req, res) => {
  try {
    await pool.query('DELETE FROM notifications WHERE id = $1 AND user_id = $2', [req.params.id, req.userId]);
    res.json({ ok: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao remover notificação.' });
  }
});

module.exports = router;
