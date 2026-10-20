const express = require('express');
const { pool } = require('../db/pool');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

const MAX_BATCH = 5000;

// Recebe um lote de amostras cruas. O app manda isto de tempos em tempos
// (não a cada pacote do BLE) para não gastar dados nem bateria à toa; o
// histórico de verdade continua sendo o banco local do aparelho.
router.post('/batch', requireAuth, async (req, res) => {
  const { deviceAddress, samples } = req.body;
  if (!Array.isArray(samples) || samples.length === 0) {
    return res.status(400).json({ error: 'samples deve ser uma lista não vazia.' });
  }
  if (samples.length > MAX_BATCH) {
    return res.status(413).json({ error: `Envie no máximo ${MAX_BATCH} amostras por lote.` });
  }

  try {
    let deviceId = null;
    if (deviceAddress) {
      const dev = await pool.query(
        'SELECT id FROM devices WHERE user_id = $1 AND address = $2',
        [req.userId, deviceAddress]
      );
      if (dev.rows.length > 0) deviceId = dev.rows[0].id;
    }

    const values = [];
    const params = [];
    let i = 1;
    for (const s of samples) {
      if (s.ts == null || !s.metric || s.value == null) continue;
      values.push(`($${i++}, $${i++}, $${i++}, $${i++}, $${i++})`);
      params.push(req.userId, deviceId, s.ts, String(s.metric).slice(0, 30), Number(s.value));
    }
    if (values.length === 0) {
      return res.status(400).json({ error: 'Nenhuma amostra válida no lote.' });
    }

    await pool.query(
      `INSERT INTO telemetry_samples (user_id, device_id, ts, metric, value) VALUES ${values.join(',')}`,
      params
    );
    res.status(201).json({ ok: true, inserted: values.length });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao gravar telemetria.' });
  }
});

// Resumo agregado de um período — o mesmo tipo de número que o app calcula
// localmente (health/Insights.java), só que a partir do que já subiu para cá.
// Útil para "ver de outro aparelho" ou para alimentar a análise no servidor.
router.get('/summary', requireAuth, async (req, res) => {
  const from = Number(req.query.from);
  const to = Number(req.query.to);
  if (!from || !to || to <= from) {
    return res.status(400).json({ error: 'Informe from e to (epoch ms) válidos.' });
  }
  try {
    const result = await pool.query(
      `SELECT metric,
              COUNT(*)::int AS count,
              AVG(value) AS avg,
              MIN(value) AS min,
              MAX(value) AS max,
              STDDEV_POP(value) AS stdev,
              SUM(value) AS sum
       FROM telemetry_samples
       WHERE user_id = $1 AND ts BETWEEN $2 AND $3
       GROUP BY metric`,
      [req.userId, from, to]
    );
    const metrics = {};
    for (const row of result.rows) {
      metrics[row.metric] = {
        count: row.count,
        avg: Number(row.avg),
        min: Number(row.min),
        max: Number(row.max),
        stdev: Number(row.stdev || 0),
        sum: Number(row.sum),
      };
    }
    res.json({ from, to, metrics });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao calcular o resumo.' });
  }
});

module.exports = router;
