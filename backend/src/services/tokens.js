const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { pool } = require('../db/pool');

const ACCESS_TTL = process.env.ACCESS_TOKEN_TTL || '1h';
const REFRESH_DAYS = Number(process.env.REFRESH_TOKEN_DAYS) || 60;
const VERIFY_HOURS = 24;

function sha256(v) {
  return crypto.createHash('sha256').update(v).digest('hex');
}

function randomToken() {
  return crypto.randomBytes(48).toString('base64url');
}

function signAccess(userId) {
  return jwt.sign({ userId, typ: 'access' }, process.env.JWT_SECRET, { expiresIn: ACCESS_TTL });
}

// O token de renovação é um valor aleatório guardado só como hash: um vazamento
// do banco não entrega tokens utilizáveis.
async function issueRefresh(userId, client = pool) {
  const raw = randomToken();
  const expires = new Date(Date.now() + REFRESH_DAYS * 86_400_000);
  await client.query(
    'INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES ($1,$2,$3)',
    [userId, sha256(raw), expires]
  );
  return raw;
}

async function issuePair(userId) {
  return { token: signAccess(userId), refreshToken: await issueRefresh(userId) };
}

// Rotação: cada renovação invalida o token usado e entrega um novo. Se alguém
// apresentar um token JÁ usado, é sinal de cópia/roubo: derruba todas as
// sessões daquela conta.
async function rotate(rawToken) {
  if (typeof rawToken !== 'string' || rawToken.length < 20 || rawToken.length > 200) return null;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const found = await client.query(
      'SELECT id, user_id, expires_at, revoked_at, revoke_reason FROM refresh_tokens WHERE token_hash = $1 FOR UPDATE',
      [sha256(rawToken)]
    );
    const row = found.rows[0];
    if (!row) { await client.query('ROLLBACK'); return null; }

    if (row.revoked_at) {
      if (row.revoke_reason !== 'rotated') {
        // Revogado por logout ou troca de senha: aparelho antigo, sem efeito colateral.
        await client.query('ROLLBACK');
        return null;
      }
      // Um token já trocado voltou a ser usado: alguém guardou uma cópia.
      await client.query(
        "UPDATE refresh_tokens SET revoked_at = now(), revoke_reason = 'revoked' WHERE user_id = $1 AND revoked_at IS NULL",
        [row.user_id]);
      await client.query('COMMIT');
      return { reuse: true };
    }
    if (new Date(row.expires_at) < new Date()) { await client.query('ROLLBACK'); return null; }

    const next = await issueRefresh(row.user_id, client);
    await client.query("UPDATE refresh_tokens SET revoked_at = now(), revoke_reason = 'rotated' WHERE id = $1", [row.id]);
    await client.query('COMMIT');
    return { userId: row.user_id, token: signAccess(row.user_id), refreshToken: next };
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    throw err;
  } finally {
    client.release();
  }
}

async function revokeToken(rawToken) {
  if (typeof rawToken !== 'string') return;
  await pool.query("UPDATE refresh_tokens SET revoked_at = now(), revoke_reason = 'revoked' WHERE token_hash = $1 AND revoked_at IS NULL", [sha256(rawToken)]);
}

async function revokeAll(userId) {
  await pool.query("UPDATE refresh_tokens SET revoked_at = now(), revoke_reason = 'revoked' WHERE user_id = $1 AND revoked_at IS NULL", [userId]);
}

async function createEmailToken(userId) {
  const raw = randomToken();
  // Uma única instrução: dois pedidos ao mesmo tempo (duplo toque em "reenviar")
  // não colidem; vale o último.
  await pool.query(
    `INSERT INTO email_verifications (user_id, token_hash, expires_at) VALUES ($1,$2,$3)
     ON CONFLICT (user_id) DO UPDATE SET token_hash = EXCLUDED.token_hash, expires_at = EXCLUDED.expires_at`,
    [userId, sha256(raw), new Date(Date.now() + VERIFY_HOURS * 3_600_000)]
  );
  return raw;
}

async function consumeEmailToken(raw) {
  if (typeof raw !== 'string' || raw.length < 20 || raw.length > 200) return null;
  const res = await pool.query(
    'DELETE FROM email_verifications WHERE token_hash = $1 RETURNING user_id, expires_at',
    [sha256(raw)]
  );
  const row = res.rows[0];
  if (!row || new Date(row.expires_at) < new Date()) return null;
  return row.user_id;
}

module.exports = { signAccess, issueRefresh, issuePair, rotate, revokeToken, revokeAll, createEmailToken, consumeEmailToken, sha256 };
