const express = require('express');
const { pool } = require('../db/pool');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

function serializeAlert(a) {
  return {
    id: String(a.id),
    ts: Number(a.ts),
    metric: a.metric,
    value: a.value == null ? null : Number(a.value),
    threshold: a.threshold == null ? null : Number(a.threshold),
    severity: a.severity,
    message: a.message,
  };
}

router.get('/', requireAuth, async (req, res) => {
  try {
    const limit = Math.min(500, Number(req.query.limit) || 100);
    const result = await pool.query(
      'SELECT * FROM alerts WHERE user_id = $1 ORDER BY ts DESC LIMIT $2',
      [req.userId, limit]
    );
    res.json(result.rows.map(serializeAlert));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar avisos.' });
  }
});

// O motor de alertas roda no aparelho (health/AlertEngine.java), perto do
// sensor; esta rota só arquiva o que ele já decidiu, para a pessoa conseguir
// ver o histórico de avisos de outro aparelho também.
router.post('/', requireAuth, async (req, res) => {
  const { alerts } = req.body;
  if (!Array.isArray(alerts) || alerts.length === 0) {
    return res.status(400).json({ error: 'alerts deve ser uma lista não vazia.' });
  }
  try {
    const values = [];
    const params = [];
    let i = 1;
    for (const a of alerts) {
      if (a.ts == null || !a.metric || !a.message) continue;
      values.push(`($${i++}, $${i++}, $${i++}, $${i++}, $${i++}, $${i++})`);
      params.push(req.userId, a.ts, String(a.metric).slice(0, 30),
        a.value == null ? null : Number(a.value),
        a.threshold == null ? null : Number(a.threshold),
        String(a.message).slice(0, 500));
    }
    if (values.length === 0) {
      return res.status(400).json({ error: 'Nenhum aviso válido na lista.' });
    }
    await pool.query(
      `INSERT INTO alerts (user_id, ts, metric, value, threshold, message) VALUES ${values.join(',')}`,
      params
    );
    res.status(201).json({ ok: true, inserted: values.length });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao gravar avisos.' });
  }
});

module.exports = router;
