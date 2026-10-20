const express = require('express');
const { pool } = require('../db/pool');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

function serializeDevice(d) {
  return {
    id: String(d.id),
    address: d.address,
    name: d.name || '',
    firmwareVersion: d.firmware_version || '',
    lastSeen: d.last_seen,
  };
}

router.get('/', requireAuth, async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT * FROM devices WHERE user_id = $1 ORDER BY last_seen DESC NULLS LAST',
      [req.userId]
    );
    res.json(result.rows.map(serializeDevice));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar dispositivos.' });
  }
});

// Chamado quando o app conecta com sucesso a um tênis. Se o endereço já
// existir para este usuário, apenas atualiza nome/firmware/último visto —
// é por isso que a coluna (user_id, address) é única.
router.post('/', requireAuth, async (req, res) => {
  const { address, name, firmwareVersion } = req.body;
  if (!address) {
    return res.status(400).json({ error: 'address é obrigatório.' });
  }
  try {
    const result = await pool.query(
      `INSERT INTO devices (user_id, address, name, firmware_version, last_seen)
       VALUES ($1, $2, $3, $4, now())
       ON CONFLICT (user_id, address) DO UPDATE SET
         name = COALESCE(NULLIF($3, ''), devices.name),
         firmware_version = COALESCE(NULLIF($4, ''), devices.firmware_version),
         last_seen = now()
       RETURNING *`,
      [req.userId, address, name || '', firmwareVersion || '']
    );
    res.status(201).json(serializeDevice(result.rows[0]));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao salvar dispositivo.' });
  }
});

router.delete('/:id', requireAuth, async (req, res) => {
  try {
    await pool.query('DELETE FROM devices WHERE id = $1 AND user_id = $2', [req.params.id, req.userId]);
    res.json({ ok: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao remover dispositivo.' });
  }
});

module.exports = router;
