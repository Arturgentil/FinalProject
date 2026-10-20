const express = require('express');
const bcrypt = require('bcrypt');
const crypto = require('crypto');
const { pool } = require('../db/pool');
const { serializeUser } = require('../utils/serializeUser');
const { validateRegister, isEmail } = require('../utils/validate');
const { rateLimit } = require('../middleware/rateLimit');
const { requireAuth } = require('../middleware/auth');
const tokens = require('../services/tokens');
const { sendVerificationEmail } = require('../services/mailer');

const router = express.Router();

const byIp = (req) => req.ip;
const byIpAndEmail = (req) => `${req.ip}|${String((req.body && req.body.email) || '').toLowerCase()}`;

const loginLimit = rateLimit({
  windowMs: 15 * 60_000, max: 10, key: byIpAndEmail,
  message: 'Muitas tentativas de entrada. Aguarde alguns minutos e tente de novo.',
});
const registerLimit = rateLimit({
  windowMs: 60 * 60_000, max: Number(process.env.RATE_REGISTER_MAX) || 8, key: byIp,
  message: 'Muitas contas criadas deste endereço. Tente mais tarde.',
});
const forgotLimit = rateLimit({
  windowMs: 60 * 60_000, max: 6, key: byIp,
  message: 'Muitos pedidos de recuperação. Tente mais tarde.',
});
const refreshLimit = rateLimit({ windowMs: 10 * 60_000, max: 40, key: byIp });
const verifyLimit = rateLimit({
  windowMs: 60 * 60_000, max: 5, key: (req) => `v|${req.userId || req.ip}`,
  message: 'Você já pediu vários e-mails de confirmação. Tente mais tarde.',
});

async function respondWithSession(res, status, user) {
  const pair = await tokens.issuePair(user.id);
  res.status(status).json({ token: pair.token, refreshToken: pair.refreshToken, user: serializeUser(user) });
}

router.post('/register', registerLimit, async (req, res) => {
  const body = req.body || {};
  const problem = validateRegister(body);
  if (problem) return res.status(400).json({ error: problem });

  const { name, username, password, birthDate, weight, height } = body;
  const email = body.email.trim().toLowerCase();
  try {
    const existing = await pool.query(
      'SELECT lower(email) AS email, lower(username) AS username FROM users WHERE lower(email) = $1 OR lower(username) = $2',
      [email, username.toLowerCase()]
    );
    if (existing.rows.some((r) => r.email === email)) {
      return res.status(409).json({ error: 'Este e-mail já está cadastrado. Tente entrar em vez de criar conta.' });
    }
    if (existing.rows.length > 0) {
      return res.status(409).json({ error: 'Este nome de usuário já está em uso. Escolha outro.' });
    }

    const passwordHash = await bcrypt.hash(password, 12);

    // Conta e token na mesma transação: se qualquer parte falhar, nada fica gravado.
    // (Antes, uma falha ao emitir o token deixava a conta criada e a segunda
    // tentativa respondia "e-mail já cadastrado".)
    const client = await pool.connect();
    let user, refreshToken;
    try {
      await client.query('BEGIN');
      const result = await client.query(
        `INSERT INTO users (name, username, email, password_hash, birth_date, weight, height)
         VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *`,
        [name.trim(), username, email, passwordHash, birthDate || '', Number(weight) || 0, Number(height) || 0]
      );
      user = result.rows[0];
      refreshToken = await tokens.issueRefresh(user.id, client);
      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      throw err;
    } finally {
      client.release();
    }

    // O e-mail de confirmação não bloqueia o cadastro: se falhar, a conta existe
    // e a pessoa pode pedir de novo.
    tokens.createEmailToken(user.id)
      .then((raw) => sendVerificationEmail(req, user, raw))
      .catch((err) => console.error('Falha ao enviar confirmação de e-mail:', err.message));

    return res.status(201).json({ token: tokens.signAccess(user.id), refreshToken, user: serializeUser(user) });
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error: 'E-mail ou nome de usuário já cadastrado.' });
    }
    console.error(err);
    res.status(500).json({ error: 'Erro ao criar a conta.' });

  }
});

router.post('/login', loginLimit, async (req, res) => {
  const { email, password } = req.body || {};
  if (typeof email !== 'string' || typeof password !== 'string' || !email || !password) {
    return res.status(400).json({ error: 'Preencha todos os campos.' });
  }
  if (password.length > 128) return res.status(401).json({ error: 'E-mail ou senha inválidos.' });
  try {
    const result = await pool.query('SELECT * FROM users WHERE lower(email) = $1', [email.trim().toLowerCase()]);
    const user = result.rows[0];
    // Compara sempre com um hash, mesmo sem usuário, para o tempo de resposta
    // não revelar se o e-mail existe.
    const hash = user ? user.password_hash : '$2b$12$C6UzMDM.H6dfI/f/IKcEeO7yZ0E0xk1m0m7V1sQb1H1k1l1m1n1o1';
    const valid = await bcrypt.compare(password, hash);
    if (!user || !valid) return res.status(401).json({ error: 'E-mail ou senha inválidos.' });
    await respondWithSession(res, 200, user);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro inesperado ao entrar.' });
  }
});

// Troca o token de renovação por um novo par. O token usado deixa de valer.
router.post('/refresh', refreshLimit, async (req, res) => {
  try {
    const out = await tokens.rotate((req.body || {}).refreshToken);
    if (!out || out.reuse) {
      return res.status(401).json({ error: out && out.reuse
        ? 'Sessão encerrada por segurança. Entre de novo.'
        : 'Sessão expirada. Entre de novo.' });
    }
    res.json({ token: out.token, refreshToken: out.refreshToken });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao renovar a sessão.' });
  }
});

router.post('/logout', async (req, res) => {
  try {
    await tokens.revokeToken((req.body || {}).refreshToken);
    res.json({ ok: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao sair.' });
  }
});

// Link recebido por e-mail. Responde uma página simples (abre no navegador).
router.get('/verify-email', async (req, res) => {
  const page = (title, msg) => res.type('html').send(
    `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">`
    + `<title>${title}</title><body style="font-family:sans-serif;max-width:480px;margin:15vh auto;padding:0 20px">`
    + `<h2>${title}</h2><p>${msg}</p></body>`);
  try {
    const userId = await tokens.consumeEmailToken(req.query.token);
    if (!userId) return page('Link inválido ou vencido', 'Peça um novo e-mail de confirmação no app, em Dados e conta.');
    await pool.query('UPDATE users SET email_verified = true WHERE id = $1', [userId]);
    page('E-mail confirmado', 'Pronto! Pode voltar ao app StepVolt.');
  } catch (err) {
    console.error(err);
    res.status(500).send('Erro ao confirmar o e-mail.');
  }
});

router.post('/resend-verification', requireAuth, verifyLimit, async (req, res) => {
  try {
    const r = await pool.query('SELECT * FROM users WHERE id = $1', [req.userId]);
    const user = r.rows[0];
    if (!user) return res.status(404).json({ error: 'Conta não encontrada.' });
    if (user.email_verified) return res.json({ ok: true, alreadyVerified: true });
    const raw = await tokens.createEmailToken(user.id);
    const out = await sendVerificationEmail(req, user, raw);
    if (!out.delivered) {
      return res.status(503).json({ error: 'O servidor ainda não tem envio de e-mail configurado (SMTP_URL). Peça ao administrador.' });
    }
    res.json({ ok: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao enviar o e-mail.' });
  }
});

// Login com Google. O app manda o idToken; o servidor confere com o Google que
// o token é válido, é para o NOSSO app (GOOGLE_CLIENT_ID) e que o e-mail foi
// verificado pelo Google. Sem GOOGLE_CLIENT_ID configurado, a rota fica desligada.
router.post('/google', loginLimit, async (req, res) => {
  const clientId = process.env.GOOGLE_CLIENT_ID;
  if (!clientId) return res.status(501).json({ error: 'Login com Google não está configurado neste servidor.' });
  const idToken = (req.body || {}).idToken;
  if (typeof idToken !== 'string' || idToken.length < 20 || idToken.length > 4096) {
    return res.status(400).json({ error: 'Token do Google ausente.' });
  }
  try {
    const r = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`);
    if (!r.ok) return res.status(401).json({ error: 'Token do Google inválido.' });
    const info = await r.json();
    const validIssuer = info.iss === 'accounts.google.com' || info.iss === 'https://accounts.google.com';
    if (info.aud !== clientId || !validIssuer || String(info.email_verified) !== 'true' || !info.sub || !isEmail(info.email)) {
      return res.status(401).json({ error: 'Token do Google inválido.' });
    }
    const email = info.email.toLowerCase();

    let found = await pool.query('SELECT * FROM users WHERE google_sub = $1', [info.sub]);
    let user = found.rows[0];
    if (!user) {
      found = await pool.query('SELECT * FROM users WHERE lower(email) = $1', [email]);
      user = found.rows[0];
      if (user) {
        await pool.query('UPDATE users SET google_sub = $1, email_verified = true WHERE id = $2', [info.sub, user.id]);
        user.email_verified = true;
      }
    }
    if (!user) {
      const base = email.split('@')[0].replace(/[^a-zA-Z0-9_.]/g, '').slice(0, 16) || 'user';
      const username = `${base}${crypto.randomInt(1000, 9999)}`;
      const unusable = await bcrypt.hash(crypto.randomBytes(32).toString('hex'), 12);
      const ins = await pool.query(
        `INSERT INTO users (name, username, email, password_hash, birth_date, weight, height, google_sub, email_verified)
         VALUES ($1,$2,$3,$4,'',0,0,$5,true) RETURNING *`,
        [String(info.name || base).slice(0, 80), username, email, unusable, info.sub]
      );
      user = ins.rows[0];
    }
    await respondWithSession(res, 200, user);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro ao entrar com o Google.' });
  }
});

// Ainda não há recuperação de senha por e-mail. Este endpoint só confirma que
// a conta existe, para o fluxo da tela funcionar de ponta a ponta.
router.post('/forgot-password', forgotLimit, async (req, res) => {
  const { email } = req.body || {};
  if (typeof email !== 'string' || !email) return res.status(400).json({ error: 'Informe o e-mail.' });
  try {
    const result = await pool.query('SELECT id FROM users WHERE lower(email) = $1', [email.trim().toLowerCase()]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Não encontramos essa conta.' });
    }
    res.json({ ok: true });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erro inesperado.' });
  }
});

module.exports = router;
