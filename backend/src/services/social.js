const { pool } = require('../db/pool');

// Publica uma atividade no feed (uma vez só). Usada pelo botão "Publicar" e
// pelo compartilhamento automático.
async function shareActivity(userId, activity) {
  if (activity.shared) return activity;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const upd = await client.query(
      'UPDATE activities SET shared = true, updated_at = now() WHERE id = $1 AND shared = false RETURNING id',
      [activity.id]
    );
    if (upd.rows.length > 0) {
      const u = await client.query('SELECT shoe_upper FROM users WHERE id = $1', [userId]);
      await client.query(
        `INSERT INTO posts (user_id, activity_id, steps, energy, duration_min, shoe_color)
         VALUES ($1,$2,$3,$4,$5,$6)`,
        [userId, activity.id, activity.steps, activity.energy, activity.duration_min, u.rows[0].shoe_upper]
      );
    }
    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    throw err;
  } finally {
    client.release();
  }
  activity.shared = true;
  return activity;
}

module.exports = { shareActivity };
