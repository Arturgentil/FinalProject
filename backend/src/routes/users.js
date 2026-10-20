const express = require('express');
const bcrypt = require('bcrypt');
const { pool } = require('../db/pool');
const { requireAuth } = require('../middleware/auth');
const { serializeUser } = require('../utils/serializeUser');
const { evaluateAchievements } = require('../services/gamification');
const { validateProfileUpdate, sniffImage, MAX_AVATAR_BYTES } = require('../utils/validate');
const { rateLimit } = require('../middleware/rateLimit');
const tokens = require('../services/tokens');

const router = express.Router();

async function loadUserWithCounts(userId) {
  const userRes = await pool.query('SELECT * FROM users WHERE id = $1', [userId]);
  if (userRes.rows.length === 0) return null;
  const user = userRes.rows[0];

  const [followingRes, followersRes, activeDaysRes] = await Promise.all([
    pool.query('SELECT COUNT(*)::int AS c FROM follows WHERE follower_id = $1', [userId]),
    pool.query('SELECT COUNT(*)::int AS c FROM follows WHERE followee_id = $1', [userId]),
    pool.query('SELECT COUNT(DISTINCT activity_date)::int AS c FROM activities WHERE user_id = $1', [userId]),
  ]);

  return serializeUser(user, {
    following: followingRes.rows[0].c,
    followers: followersRes.rows[0].c,
    activeDays: activeDaysRes.rows[0].c,
  });
}

router.get('/me', requireAuth, async (req, res) => {
  try {
    const user = await loadUserWithCounts(req.userId);
    if (!user) return res.status(404).json({ error: 'Usuário não encontrado.' });
    res.json(user);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao buscar perfil.' });
  }
});

router.put('/me', requireAuth, async (req, res) => {
  const body = req.body || {};
  const problem = validateProfileUpdate(body);
  if (problem) return res.status(400).json({ error: problem });
  const { name, username, birthDate, weight, height, bio, publicProfile, commentPolicy, autoShare,
    notifGoal, notifRecord, notifSocial } = body;
  try {
    await pool.query(
      `UPDATE users SET name = COALESCE($1, name), username = COALESCE($2, username),
       birth_date = COALESCE($3, birth_date), weight = COALESCE($4, weight),
       height = COALESCE($5, height), bio = COALESCE($6, bio),
       public_profile = COALESCE($7, public_profile), comment_policy = COALESCE($8, comment_policy),
       auto_share = COALESCE($9, auto_share),
       notif_goal = COALESCE($10, notif_goal), notif_record = COALESCE($11, notif_record),
       notif_social = COALESCE($12, notif_social)
       WHERE id = $13`,
      [name && name.trim(), username, birthDate, weight, height, bio && bio.trim(),
       publicProfile, commentPolicy, autoShare, notifGoal, notifRecord, notifSocial, req.userId]
    );
    const user = await loadUserWithCounts(req.userId);
    res.json(user);
  } catch (err) {
    console.error(err);
    if (err.code === '23505') {
      return res.status(409).json({ error: 'Nome de usuário já está em uso.' });
    }
    res.status(500).json({ error: 'Erro ao salvar perfil.' });
  }
});

// ---------------------------------------------------------------------------
// Foto de perfil. O app manda a imagem já reduzida (512x512 JPEG) em base64;
// o servidor confere o formato pelos bytes, limita o tamanho e guarda no banco.
// avatar_url passa a apontar para a rota abaixo, então feed, busca, ranking e
// notificações (que já liam avatar_url) mostram a foto nova sem mudar.
// ---------------------------------------------------------------------------
const avatarLimit = rateLimit({
  windowMs: 60 * 60_000, max: 15, key: (req) => `av|${req.userId || req.ip}`,
  message: 'Você trocou a foto muitas vezes seguidas. Tente mais tarde.',
});

router.put('/me/avatar', requireAuth, avatarLimit, async (req, res) => {
  let raw = (req.body || {}).image;
  if (typeof raw !== 'string' || raw.length < 100) return res.status(400).json({ error: 'Imagem ausente.' });
  const comma = raw.indexOf(',');
  if (raw.startsWith('data:') && comma > 0) raw = raw.slice(comma + 1);
  if (raw.length > Math.ceil(MAX_AVATAR_BYTES * 4 / 3) + 8) {
    return res.status(413).json({ error: 'Imagem grande demais (máximo 400 KB).' });
  }
  if (!/^[A-Za-z0-9+/=\s]+$/.test(raw)) return res.status(400).json({ error: 'Imagem inválida.' });
  const buf = Buffer.from(raw, 'base64');
  if (buf.length < 64) return res.status(400).json({ error: 'Imagem inválida.' });
  if (buf.length > MAX_AVATAR_BYTES) return res.status(413).json({ error: 'Imagem grande demais (máximo 400 KB).' });
  const mime = sniffImage(buf);
  if (!mime) return res.status(400).json({ error: 'Use uma imagem JPEG, PNG ou WebP.' });
  try {
    const ts = Date.now();
    await pool.query(
      `UPDATE users SET avatar_data = $1, avatar_mime = $2, avatar_updated_at = now(),
              avatar_url = $3 WHERE id = $4`,
      [buf, mime, `/api/users/${req.userId}/avatar?v=${ts}`, req.userId]
    );
    res.json(await loadUserWithCounts(req.userId));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao salvar a foto.' });
  }
});

router.delete('/me/avatar', requireAuth, async (req, res) => {
  try {
    await pool.query(
      `UPDATE users SET avatar_data = NULL, avatar_mime = NULL, avatar_updated_at = NULL, avatar_url = '' WHERE id = $1`,
      [req.userId]
    );
    res.json(await loadUserWithCounts(req.userId));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao remover a foto.' });
  }
});

// Pública de propósito: o Glide do app e as miniaturas do feed carregam sem
// cabeçalho de autenticação. A URL leva ?v=<versão>, então o cache pode ser longo.
router.get('/:id/avatar', async (req, res) => {
  if (!/^[0-9]{1,9}$/.test(req.params.id)) return res.status(404).end();
  try {
    const r = await pool.query(
      'SELECT avatar_data, avatar_mime, avatar_updated_at FROM users WHERE id = $1', [req.params.id]);
    const row = r.rows[0];
    if (!row || !row.avatar_data) return res.status(404).end();
    const etag = `"${new Date(row.avatar_updated_at).getTime()}"`;
    res.set('ETag', etag);
    res.set('Cache-Control', 'public, max-age=86400');
    res.set('Content-Type', row.avatar_mime || 'image/jpeg');
    if (req.headers['if-none-match'] === etag) return res.status(304).end();
    res.send(row.avatar_data);
  } catch (err) {
    console.error(err);
    res.status(500).end();
  }
});

router.put('/me/shoe', requireAuth, async (req, res) => {
  const { upper, sole, laces, details, theme } = req.body;
  try {
    await pool.query(
      `UPDATE users SET shoe_upper = $1, shoe_sole = $2, shoe_laces = $3,
       shoe_details = $4, shoe_theme = $5 WHERE id = $6`,
      [upper, sole, laces, details, theme, req.userId]
    );
    const user = await loadUserWithCounts(req.userId);
    res.json(user);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao salvar a personalização.' });
  }
});

router.get('/search', requireAuth, async (req, res) => {
  const q = (req.query.q || '').trim();
  try {
    let rows;
    if (q.length === 0) {
      // sem busca: mostra os usuários em destaque (mais seguidores)
      const result = await pool.query(
        `SELECT u.*, (SELECT COUNT(*)::int FROM follows f WHERE f.followee_id = u.id) AS followers_count
         FROM users u WHERE u.id != $1
         ORDER BY followers_count DESC LIMIT 6`,
        [req.userId]
      );
      rows = result.rows.map((r, idx) => ({ ...r, featured: idx < 4 }));
    } else {
      const result = await pool.query(
        `SELECT u.*, (SELECT COUNT(*)::int FROM follows f WHERE f.followee_id = u.id) AS followers_count
         FROM users u WHERE u.id != $1 AND (LOWER(u.name) LIKE $2 OR LOWER(u.username) LIKE $2)
         ORDER BY followers_count DESC LIMIT 30`,
        [req.userId, `%${q.toLowerCase()}%`]
      );
      rows = result.rows.map((r) => ({ ...r, featured: false }));
    }

    const followingRes = await pool.query('SELECT followee_id FROM follows WHERE follower_id = $1', [req.userId]);
    const followingSet = new Set(followingRes.rows.map((r) => r.followee_id));

    const totalsRes = await pool.query(
      `SELECT user_id, COALESCE(SUM(steps),0)::bigint AS steps, COALESCE(SUM(energy),0)::numeric AS energy
       FROM activities WHERE user_id = ANY($1) GROUP BY user_id`,
      [rows.map((r) => r.id)]
    );
    const totalsMap = new Map(totalsRes.rows.map((r) => [r.user_id, r]));

    res.json(
      rows.map((r) => ({
        id: String(r.id),
        name: r.name,
        username: r.username,
        avatarUrl: r.avatar_url || '',
        steps: Number(totalsMap.get(r.id)?.steps || 0),
        energy: Number(totalsMap.get(r.id)?.energy || 0),
        followers: r.followers_count,
        following: followingSet.has(r.id),
        featured: r.featured,
      }))
    );
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao buscar usuários.' });
  }
});

router.post('/:id/toggle-follow', requireAuth, async (req, res) => {
  const targetId = Number(req.params.id);
  if (targetId === req.userId) {
    return res.status(400).json({ error: 'Você não pode seguir a si mesmo.' });
  }
  try {
    const existing = await pool.query(
      'SELECT 1 FROM follows WHERE follower_id = $1 AND followee_id = $2',
      [req.userId, targetId]
    );
    let following;
    if (existing.rows.length > 0) {
      await pool.query('DELETE FROM follows WHERE follower_id = $1 AND followee_id = $2', [req.userId, targetId]);
      following = false;
    } else {
      await pool.query('INSERT INTO follows (follower_id, followee_id) VALUES ($1,$2)', [req.userId, targetId]);
      following = true;
      await pool.query(
        `INSERT INTO notifications (user_id, type, message, actor_avatar_url)
         SELECT $1, 'follow', name || ' começou a seguir você', avatar_url FROM users
         WHERE id = $2 AND (SELECT notif_social FROM users WHERE id = $1)`,
        [targetId, req.userId]
      );
      await evaluateAchievements(targetId);
    }
    res.json({ following });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao seguir/deixar de seguir.' });
  }
});

async function buildSocialUserList(userRows, requestingUserId) {
  if (userRows.length === 0) return [];
  const ids = userRows.map((r) => r.id);

  const [followingRes, totalsRes, followersCountRes] = await Promise.all([
    pool.query('SELECT followee_id FROM follows WHERE follower_id = $1', [requestingUserId]),
    pool.query(
      `SELECT user_id, COALESCE(SUM(steps),0)::bigint AS steps, COALESCE(SUM(energy),0)::numeric AS energy
       FROM activities WHERE user_id = ANY($1) GROUP BY user_id`,
      [ids]
    ),
    pool.query(
      `SELECT followee_id, COUNT(*)::int AS c FROM follows WHERE followee_id = ANY($1) GROUP BY followee_id`,
      [ids]
    ),
  ]);
  const followingSet = new Set(followingRes.rows.map((r) => r.followee_id));
  const totalsMap = new Map(totalsRes.rows.map((r) => [r.user_id, r]));
  const followersMap = new Map(followersCountRes.rows.map((r) => [r.followee_id, r.c]));

  return userRows.map((r) => ({
    id: String(r.id),
    name: r.name,
    username: r.username,
    avatarUrl: r.avatar_url || '',
    steps: Number(totalsMap.get(r.id)?.steps || 0),
    energy: Number(totalsMap.get(r.id)?.energy || 0),
    followers: followersMap.get(r.id) || 0,
    following: followingSet.has(r.id),
    featured: false,
  }));
}

router.get('/me/following', requireAuth, async (req, res) => {
  try {
    const result = await pool.query(
      `SELECT u.* FROM follows f JOIN users u ON u.id = f.followee_id WHERE f.follower_id = $1`,
      [req.userId]
    );
    res.json(await buildSocialUserList(result.rows, req.userId));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar quem você segue.' });
  }
});

router.get('/me/followers', requireAuth, async (req, res) => {
  try {
    const result = await pool.query(
      `SELECT u.* FROM follows f JOIN users u ON u.id = f.follower_id WHERE f.followee_id = $1`,
      [req.userId]
    );
    res.json(await buildSocialUserList(result.rows, req.userId));
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao carregar seguidores.' });
  }
});

router.put('/me/password', requireAuth, async (req, res) => {
  const { currentPassword, newPassword } = req.body || {};
  if (typeof currentPassword !== 'string' || typeof newPassword !== 'string' || !currentPassword || !newPassword) {
    return res.status(400).json({ error: 'Informe a senha atual e a nova senha.' });
  }
  if (newPassword.length < 8 || newPassword.length > 128) {
    return res.status(400).json({ error: 'A nova senha deve ter de 8 a 128 caracteres.' });
  }
  try {
    const result = await pool.query('SELECT password_hash FROM users WHERE id = $1', [req.userId]);
    const valid = await bcrypt.compare(currentPassword, result.rows[0].password_hash);
    if (!valid) return res.status(401).json({ error: 'Senha atual incorreta.' });
    const newHash = await bcrypt.hash(newPassword, 12);
    await pool.query('UPDATE users SET password_hash = $1 WHERE id = $2', [newHash, req.userId]);
    // Trocar a senha encerra as outras sessões (o token de acesso atual vale até expirar, no máximo 1 h).
    await tokens.revokeAll(req.userId);
    const pair = await tokens.issuePair(req.userId);
    res.json({ ok: true, token: pair.token, refreshToken: pair.refreshToken });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao alterar senha.' });
  }
});

// Excluir a conta pede a senha: um token roubado não basta para apagar tudo.
// Quem entrou só com o Google (sem senha própria) confirma digitando EXCLUIR.
router.delete('/me', requireAuth, async (req, res) => {
  const { password, confirm } = req.body || {};
  try {
    const r = await pool.query('SELECT password_hash, google_sub FROM users WHERE id = $1', [req.userId]);
    const row = r.rows[0];
    if (!row) return res.status(404).json({ error: 'Conta não encontrada.' });
    if (row.google_sub) {
      if (confirm !== 'EXCLUIR') return res.status(400).json({ error: 'Digite EXCLUIR para confirmar.' });
    } else {
      if (typeof password !== 'string' || !password) return res.status(400).json({ error: 'Informe sua senha para excluir a conta.' });
      if (!(await bcrypt.compare(password, row.password_hash))) return res.status(401).json({ error: 'Senha incorreta.' });
    }
    await pool.query('DELETE FROM users WHERE id = $1', [req.userId]);
    res.json({ ok: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao excluir a conta.' });
  }
});

module.exports = router;
