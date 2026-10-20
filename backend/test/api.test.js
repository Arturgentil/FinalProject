// Testes de integração da API contra um PostgreSQL de verdade.
//
//   DATABASE_URL=postgresql://... JWT_SECRET=<24+ caracteres> npm test
//
// O banco precisa já ter as migrações (node src/db/migrate.js). Os testes criam
// usuários com e-mails únicos e apagam tudo no final. Sem DATABASE_URL, pulam.
const test = require('node:test');
const assert = require('node:assert/strict');

// Os testes criam muitas contas do mesmo IP; sobe o limite de cadastro só aqui.
process.env.RATE_REGISTER_MAX = '1000';

const HAS_DB = !!process.env.DATABASE_URL && !!process.env.JWT_SECRET;
const opts = { skip: HAS_DB ? false : 'defina DATABASE_URL e JWT_SECRET' };

let server, base, pool, tokens;
const stamp = Date.now();
const created = [];

async function api(method, path, { body, token, raw } = {}) {
  const res = await fetch(base + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: raw !== undefined ? raw : body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) { /* html */ }
  return { status: res.status, json, text, headers: res.headers };
}

async function newUser(tag) {
  const email = `t${stamp}${tag}@teste.local`;
  const r = await api('POST', '/api/auth/register', {
    body: { name: `Teste ${tag}`, username: `t${stamp}${tag}`.slice(0, 24), email, password: 'senha-forte-123' },
  });
  assert.equal(r.status, 201, r.text);
  created.push(email);
  return { email, token: r.json.token, refresh: r.json.refreshToken, id: r.json.user.id, user: r.json.user };
}

test.before(async () => {
  if (!HAS_DB) return;
  const app = require('../src/server');
  ({ pool } = require('../src/db/pool'));
  tokens = require('../src/services/tokens');
  await new Promise((ok) => { server = app.listen(0, ok); });
  base = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => {
  if (!HAS_DB) return;
  if (created.length) await pool.query('DELETE FROM users WHERE email = ANY($1)', [created]);
  await new Promise((ok) => server.close(ok));
  await pool.end();
});

test('cadastro valida os campos', opts, async () => {
  const bad = (over) => api('POST', '/api/auth/register', {
    body: { name: 'Ana', username: 'ana_ok', email: 'ana@x.com', password: 'senha-forte-123', ...over },
  });
  assert.equal((await bad({ password: '12345' })).status, 400);
  assert.equal((await bad({ email: 'sem-arroba' })).status, 400);
  assert.equal((await bad({ username: 'a b' })).status, 400);
  assert.equal((await bad({ name: '' })).status, 400);
  assert.equal((await bad({ weight: 9999 })).status, 400);
});

test('cadastro devolve par de tokens e e-mail não confirmado', opts, async () => {
  const u = await newUser('reg');
  assert.ok(u.token && u.refresh);
  assert.equal(u.user.emailVerified, false);
  const dup = await api('POST', '/api/auth/register', {
    body: { name: 'Outra', username: 'outro_nome', email: u.email.toUpperCase(), password: 'senha-forte-123' },
  });
  assert.equal(dup.status, 409);
});

test('login: certo, errado e limite de tentativas', opts, async () => {
  const u = await newUser('log');
  const ok = await api('POST', '/api/auth/login', { body: { email: u.email, password: 'senha-forte-123' } });
  assert.equal(ok.status, 200);
  assert.ok(ok.json.refreshToken);

  const wrongEmail = `x${stamp}@teste.local`;
  let last;
  for (let i = 0; i < 10; i++) {
    last = await api('POST', '/api/auth/login', { body: { email: wrongEmail, password: 'errada' } });
    assert.equal(last.status, 401);
  }
  last = await api('POST', '/api/auth/login', { body: { email: wrongEmail, password: 'errada' } });
  assert.equal(last.status, 429);
  assert.ok(last.headers.get('retry-after'));
});

test('refresh troca o token e detecta reuso', opts, async () => {
  const u = await newUser('ref');
  const r1 = await api('POST', '/api/auth/refresh', { body: { refreshToken: u.refresh } });
  assert.equal(r1.status, 200);
  assert.notEqual(r1.json.refreshToken, u.refresh);
  const me = await api('GET', '/api/activities', { token: r1.json.token });
  assert.equal(me.status, 200);

  // token antigo já foi usado: reuso derruba a sessão inteira
  const reuse = await api('POST', '/api/auth/refresh', { body: { refreshToken: u.refresh } });
  assert.equal(reuse.status, 401);
  const afterReuse = await api('POST', '/api/auth/refresh', { body: { refreshToken: r1.json.refreshToken } });
  assert.equal(afterReuse.status, 401);

  assert.equal((await api('POST', '/api/auth/refresh', { body: { refreshToken: 'lixo' } })).status, 401);
  assert.equal((await api('POST', '/api/auth/refresh', { body: {} })).status, 401);
});

test('logout invalida o token de renovação', opts, async () => {
  const u = await newUser('out');
  assert.equal((await api('POST', '/api/auth/logout', { body: { refreshToken: u.refresh } })).status, 200);
  assert.equal((await api('POST', '/api/auth/refresh', { body: { refreshToken: u.refresh } })).status, 401);
});

test('rotas protegidas exigem token válido', opts, async () => {
  assert.equal((await api('GET', '/api/activities')).status, 401);
  assert.equal((await api('GET', '/api/activities', { token: 'abc.def.ghi' })).status, 401);
  assert.equal((await api('GET', '/api/challenges')).status, 401);
});

test('atividades: validação, paginação e sincronização incremental', opts, async () => {
  const u = await newUser('act');
  const post = (b) => api('POST', '/api/activities', { token: u.token, body: b });

  assert.equal((await post({ steps: -5, energy: 1 })).status, 400);
  assert.equal((await post({ steps: 100, energy: 'abc' })).status, 400);
  assert.equal((await post({ steps: 100, energy: 1, bpmSamples: [10, 'x'] })).status, 400);
  assert.equal((await post({ steps: 100, energy: 1, kind: 'voar' })).status, 400);

  for (let i = 1; i <= 5; i++) {
    const r = await post({ date: '2026-10-0' + i, steps: i * 1000, energy: i * 10, distance: i, duration: 30, kind: 'run', elevationGain: 12 });
    assert.equal(r.status, 201, r.text);
    assert.equal(r.json.kind, 'run');
  }
  const page1 = await api('GET', '/api/activities?limit=2&offset=0', { token: u.token });
  assert.equal(page1.json.length, 2);
  assert.equal(page1.headers.get('x-total-count'), '5');
  const page3 = await api('GET', '/api/activities?limit=2&offset=4', { token: u.token });
  assert.equal(page3.json.length, 1);
  assert.equal((await api('GET', '/api/activities?since=nao-e-data', { token: u.token })).status, 400);

  const mark = new Date(Date.now() + 1500).toISOString();
  const none = await api('GET', `/api/activities?since=${encodeURIComponent(mark)}`, { token: u.token });
  assert.equal(none.json.length, 0);

  await new Promise((r) => setTimeout(r, 1700));
  const sixth = await post({ date: '2026-10-06', steps: 600, energy: 6 });
  const changed = await api('GET', `/api/activities?since=${encodeURIComponent(mark)}`, { token: u.token });
  assert.equal(changed.json.length, 1);
  assert.equal(changed.json[0].id, sixth.json.id);

  assert.equal((await api('GET', '/api/activities/abc', { token: u.token })).status, 404);
});

test('um usuário não vê atividade de outro', opts, async () => {
  const a = await newUser('pa');
  const b = await newUser('pb');
  const act = await api('POST', '/api/activities', { token: a.token, body: { steps: 10, energy: 1 } });
  assert.equal(act.status, 201, act.text);
  assert.equal((await api('GET', `/api/activities/${act.json.id}`, { token: b.token })).status, 404);
  assert.equal((await api('PUT', `/api/activities/${act.json.id}/share`, { token: b.token })).status, 404);
});

test('desafios: criar, visibilidade, entrar, ranking e regras', opts, async () => {
  const owner = await newUser('co');
  const friend = await newUser('cf');
  const stranger = await newUser('cs');

  assert.equal((await api('POST', '/api/challenges', { token: owner.token, body: { title: 'ab', metric: 'steps', days: 7 } })).status, 400);
  assert.equal((await api('POST', '/api/challenges', { token: owner.token, body: { title: 'Semana forte', metric: 'voo', days: 7 } })).status, 400);
  assert.equal((await api('POST', '/api/challenges', { token: owner.token, body: { title: 'Semana forte', metric: 'steps', days: 365 } })).status, 400);

  const made = await api('POST', '/api/challenges', { token: owner.token, body: { title: 'Semana forte', metric: 'steps', days: 7, targetValue: 50000 } });
  assert.equal(made.status, 201, made.text);
  const id = made.json.id;
  assert.equal(made.json.joined, true);
  assert.equal(made.json.mine, true);
  assert.equal(made.json.participants, 1);

  // quem não segue o dono não vê nem entra
  assert.equal((await api('POST', `/api/challenges/${id}/join`, { token: friend.token })).status, 404);
  assert.equal((await api('GET', `/api/challenges/${id}/leaderboard`, { token: stranger.token })).status, 404);

  await pool.query('INSERT INTO follows (follower_id, followee_id) VALUES ($1,$2)', [friend.id, owner.id]);
  const listed = await api('GET', '/api/challenges', { token: friend.token });
  assert.equal(listed.json.some((c) => c.id === id), true);
  const joined = await api('POST', `/api/challenges/${id}/join`, { token: friend.token });
  assert.equal(joined.status, 200);
  assert.equal(joined.json.participants, 2);

  await api('POST', '/api/activities', { token: owner.token, body: { steps: 3000, energy: 30 } });
  await api('POST', '/api/activities', { token: friend.token, body: { steps: 5000, energy: 20 } });
  const board = await api('GET', `/api/challenges/${id}/leaderboard`, { token: owner.token });
  assert.equal(board.json[0].value, 5000);
  assert.equal(board.json[0].rank, 1);
  assert.equal(board.json[1].value, 3000);
  assert.equal(board.json[1].isMe, true);

  // dono não sai; participante sai
  assert.equal((await api('POST', `/api/challenges/${id}/leave`, { token: owner.token })).status, 400);
  assert.equal((await api('POST', `/api/challenges/${id}/leave`, { token: friend.token })).status, 200);
  assert.equal((await api('POST', '/api/challenges/abc/join', { token: owner.token })).status, 404);

  // desafio encerrado não aceita novos participantes
  await pool.query("UPDATE challenges SET ends_at = now() - interval '1 hour' WHERE id = $1", [id]);
  assert.equal((await api('POST', `/api/challenges/${id}/join`, { token: friend.token })).status, 400);
});

test('ranking compara você e quem você segue', opts, async () => {
  const me = await newUser('lm');
  const f = await newUser('lf');
  const other = await newUser('lo');
  await pool.query('INSERT INTO follows (follower_id, followee_id) VALUES ($1,$2)', [me.id, f.id]);
  await api('POST', '/api/activities', { token: me.token, body: { steps: 100, energy: 5 } });
  await api('POST', '/api/activities', { token: f.token, body: { steps: 100, energy: 50 } });
  await api('POST', '/api/activities', { token: other.token, body: { steps: 100, energy: 500 } });
  const r = await api('GET', '/api/leaderboard?period=week&metric=energy', { token: me.token });
  assert.equal(r.status, 200);
  assert.equal(r.json.length, 2); // 'other' não aparece
  assert.equal(r.json[0].value, 50);
  assert.equal(r.json[1].isMe, true);
  const bad = await api('GET', '/api/leaderboard?metric=drop%20table', { token: me.token });
  assert.equal(bad.status, 200); // métrica inválida cai no padrão, nunca vai para o SQL
});

test('confirmação de e-mail: link vale uma vez', opts, async () => {
  const u = await newUser('mail');
  const raw = await tokens.createEmailToken(u.id);
  const ok = await api('GET', `/api/auth/verify-email?token=${encodeURIComponent(raw)}`);
  assert.equal(ok.status, 200);
  assert.match(ok.text, /E-mail confirmado/);
  const row = await pool.query('SELECT email_verified FROM users WHERE id = $1', [u.id]);
  assert.equal(row.rows[0].email_verified, true);
  const again = await api('GET', `/api/auth/verify-email?token=${encodeURIComponent(raw)}`);
  assert.match(again.text, /inválido ou vencido/);
  const fake = await api('GET', '/api/auth/verify-email?token=qualquercoisa');
  assert.match(fake.text, /inválido ou vencido/);
});

test('reenviar confirmação sem SMTP avisa em vez de fingir', opts, async () => {
  const u = await newUser('rs');
  const r = await api('POST', '/api/auth/resend-verification', { token: u.token });
  assert.equal(r.status, 503);
});

test('login com Google fica desligado sem GOOGLE_CLIENT_ID', opts, async () => {
  if (process.env.GOOGLE_CLIENT_ID) return;
  const r = await api('POST', '/api/auth/google', { body: { idToken: 'x'.repeat(40) } });
  assert.equal(r.status, 501);
});

test('JSON inválido, cabeçalhos de segurança e rota inexistente', opts, async () => {
  const bad = await api('POST', '/api/auth/login', { raw: '{nao-e-json' });
  assert.equal(bad.status, 400);
  const nf = await api('GET', '/api/nao-existe');
  assert.equal(nf.status, 404);
  const h = await api('GET', '/api/health');
  assert.equal(h.headers.get('x-content-type-options'), 'nosniff');
  assert.equal(h.headers.get('x-powered-by'), null);
});

function fakeJpeg(extra = 400) {
  return Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10]), Buffer.alloc(extra, 7)]);
}

test('foto de perfil: envia, serve, troca e remove', opts, async () => {
  const u = await newUser('av');
  const img = fakeJpeg();
  const up = await api('PUT', '/api/users/me/avatar', { token: u.token, body: { image: img.toString('base64') } });
  assert.equal(up.status, 200, up.text);
  assert.match(up.json.avatarUrl, new RegExp(`^/api/users/${u.id}/avatar\\?v=\\d+$`));

  const got = await fetch(base + up.json.avatarUrl);   // sem Authorization: rota pública
  assert.equal(got.status, 200);
  assert.equal(got.headers.get('content-type'), 'image/jpeg');
  assert.equal(got.headers.get('x-content-type-options'), 'nosniff');
  assert.deepEqual(Buffer.from(await got.arrayBuffer()), img);
  const etag = got.headers.get('etag');
  const cached = await fetch(base + up.json.avatarUrl, { headers: { 'If-None-Match': etag } });
  assert.equal(cached.status, 304);

  // aceita data URL e PNG
  const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(200, 1)]);
  const up2 = await api('PUT', '/api/users/me/avatar', { token: u.token, body: { image: 'data:image/png;base64,' + png.toString('base64') } });
  assert.equal(up2.status, 200);
  assert.equal((await fetch(base + up2.json.avatarUrl)).headers.get('content-type'), 'image/png');
  assert.notEqual(up2.json.avatarUrl, up.json.avatarUrl);

  // perfil mostra a foto; remover limpa e a rota passa a dar 404
  const me = await api('GET', '/api/users/me', { token: u.token });
  assert.equal(me.json.avatarUrl, up2.json.avatarUrl);
  const del = await api('DELETE', '/api/users/me/avatar', { token: u.token });
  assert.equal(del.status, 200);
  assert.equal(del.json.avatarUrl, '');
  assert.equal((await fetch(base + `/api/users/${u.id}/avatar`)).status, 404);
});

test('foto de perfil: rejeita arquivo falso, grande demais e sem login', opts, async () => {
  const u = await newUser('av2');
  const put = (image, token = u.token) => api('PUT', '/api/users/me/avatar', { token, body: { image } });
  assert.equal((await put(Buffer.alloc(500, 65).toString('base64'))).status, 400);                 // não é imagem
  assert.equal((await put(Buffer.from('<svg onload=alert(1)>'.repeat(20)).toString('base64'))).status, 400);
  assert.equal((await put(fakeJpeg(500 * 1024).toString('base64'))).status, 413);                   // > 400 KB
  assert.equal((await put('abc')).status, 400);
  assert.equal((await put('%%%%'.repeat(40))).status, 400);
  assert.equal((await api('PUT', '/api/users/me/avatar', { body: { image: fakeJpeg().toString('base64') } })).status, 401);
  assert.equal((await fetch(base + '/api/users/abc/avatar')).status, 404);
  assert.equal((await fetch(base + '/api/users/999999999/avatar')).status, 404);
});

test('perfil: valida campos e guarda preferências de privacidade', opts, async () => {
  const u = await newUser('pf');
  const put = (b) => api('PUT', '/api/users/me', { token: u.token, body: b });
  assert.equal((await put({ name: 'A' })).status, 400);
  assert.equal((await put({ username: 'a b' })).status, 400);
  assert.equal((await put({ birthDate: '31/12/1990' })).status, 400);
  assert.equal((await put({ birthDate: '2999-01-01' })).status, 400);
  assert.equal((await put({ birthDate: '1990-02-31' })).status, 400);
  assert.equal((await put({ weight: 900 })).status, 400);
  assert.equal((await put({ bio: 'x'.repeat(281) })).status, 400);
  assert.equal((await put({ commentPolicy: 'todos' })).status, 400);
  assert.equal((await put({ publicProfile: 'sim' })).status, 400);

  const ok = await put({ name: 'Nome Novo', birthDate: '1990-05-17', weight: 70.5, height: 175, bio: 'oi',
    publicProfile: false, commentPolicy: 'followers', autoShare: true });
  assert.equal(ok.status, 200, ok.text);
  assert.equal(ok.json.name, 'Nome Novo');
  assert.equal(ok.json.publicProfile, false);
  assert.equal(ok.json.commentPolicy, 'followers');
  assert.equal(ok.json.autoShare, true);
  // mandar só um campo não zera os outros
  const only = await put({ bio: 'novo' });
  assert.equal(only.json.publicProfile, false);
  assert.equal(only.json.weight, 70.5);
});

test('privacidade: perfil privado some do feed, comentários seguem a política, auto-compartilhar publica', opts, async () => {
  const owner = await newUser('pv1');
  const fan = await newUser('pv2');
  const stranger = await newUser('pv3');
  await api('PUT', '/api/users/me', { token: owner.token, body: { autoShare: true } });
  const act = await api('POST', '/api/activities', { token: owner.token, body: { steps: 1234, energy: 12 } });
  assert.equal(act.status, 201, act.text);
  assert.equal(act.json.shared, true);                        // publicou sozinho

  const feedOf = async (t) => (await api('GET', '/api/feed', { token: t })).json;
  const mine = (await feedOf(owner.token)).find((p) => p.user === `t${stamp}pv1`.slice(0, 24));
  assert.ok(mine, 'dono vê o próprio post');
  const postId = mine.id;
  assert.ok((await feedOf(stranger.token)).some((p) => p.id === postId), 'perfil público aparece para todos');

  // vira privado: estranho deixa de ver; seguidor continua vendo
  await api('PUT', '/api/users/me', { token: owner.token, body: { publicProfile: false } });
  await pool.query('INSERT INTO follows (follower_id, followee_id) VALUES ($1,$2)', [fan.id, owner.id]);
  assert.equal((await feedOf(stranger.token)).some((p) => p.id === postId), false);
  assert.equal((await feedOf(fan.token)).some((p) => p.id === postId), true);
  assert.equal((await api('POST', `/api/feed/${postId}/comments`, { token: stranger.token, body: { text: 'oi' } })).status, 404);

  // política de comentário
  await api('PUT', '/api/users/me', { token: owner.token, body: { commentPolicy: 'followers' } });
  assert.equal((await api('POST', `/api/feed/${postId}/comments`, { token: fan.token, body: { text: 'oi' } })).status, 201);
  await api('PUT', '/api/users/me', { token: owner.token, body: { commentPolicy: 'none', publicProfile: true } });
  assert.equal((await api('POST', `/api/feed/${postId}/comments`, { token: stranger.token, body: { text: 'oi' } })).status, 403);
  assert.equal((await api('POST', `/api/feed/${postId}/comments`, { token: owner.token, body: { text: 'eu mesmo' } })).status, 201);
  await api('PUT', '/api/users/me', { token: owner.token, body: { commentPolicy: 'followers' } });
  assert.equal((await api('POST', `/api/feed/${postId}/comments`, { token: stranger.token, body: { text: 'oi' } })).status, 403);

  // validações do comentário
  assert.equal((await api('POST', `/api/feed/${postId}/comments`, { token: owner.token, body: { text: 'x'.repeat(501) } })).status, 400);
  assert.equal((await api('POST', '/api/feed/99999999/comments', { token: owner.token, body: { text: 'oi' } })).status, 404);
  assert.equal((await api('POST', '/api/feed/abc/comments', { token: owner.token, body: { text: 'oi' } })).status, 404);
});

test('senha: troca exige a atual, encerra outras sessões; excluir conta exige senha', opts, async () => {
  const u = await newUser('pw');
  const put = (b, t = u.token) => api('PUT', '/api/users/me/password', { token: t, body: b });
  assert.equal((await put({ currentPassword: 'errada-errada', newPassword: 'nova-senha-123' })).status, 401);
  assert.equal((await put({ currentPassword: 'senha-forte-123', newPassword: '1234567' })).status, 400);
  const ok = await put({ currentPassword: 'senha-forte-123', newPassword: 'nova-senha-123' });
  assert.equal(ok.status, 200);
  assert.ok(ok.json.token && ok.json.refreshToken);
  // o token de renovação antigo morreu (aparelho antigo), mas isso NÃO derruba a sessão nova
  assert.equal((await api('POST', '/api/auth/refresh', { body: { refreshToken: u.refresh } })).status, 401);
  assert.equal((await api('POST', '/api/auth/refresh', { body: { refreshToken: u.refresh } })).status, 401);
  const renewed = await api('POST', '/api/auth/refresh', { body: { refreshToken: ok.json.refreshToken } });
  assert.equal(renewed.status, 200);
  ok.json.token = renewed.json.token;
  assert.equal((await api('POST', '/api/auth/login', { body: { email: u.email, password: 'nova-senha-123' } })).status, 200);

  const del = (b) => api('DELETE', '/api/users/me', { token: ok.json.token, body: b });
  assert.equal((await del({})).status, 400);
  assert.equal((await del({ password: 'senha-errada-aqui' })).status, 401);
  assert.equal((await del({ password: 'nova-senha-123' })).status, 200);
  assert.equal((await api('POST', '/api/auth/login', { body: { email: u.email, password: 'nova-senha-123' } })).status, 401);
});

test('confirmação de e-mail: pedidos simultâneos não colidem', opts, async () => {
  const u = await newUser('race');
  const out = await Promise.all([1, 2, 3, 4, 5].map(() => tokens.createEmailToken(u.id)));
  assert.equal(new Set(out).size, 5);
  const rows = await pool.query('SELECT COUNT(*)::int AS n FROM email_verifications WHERE user_id = $1', [u.id]);
  assert.equal(rows.rows[0].n, 1);
});

test('preferências de aviso: o servidor só cria a notificação se estiver ligada', opts, async () => {
  const author = await newUser('nt1');
  const fan = await newUser('nt2');
  const act = await api('POST', '/api/activities', { token: author.token, body: { steps: 10, energy: 1 } });
  await api('PUT', `/api/activities/${act.json.id}/share`, { token: author.token });
  const feed = (await api('GET', '/api/feed', { token: fan.token })).json;
  const post = feed.find((p) => p.user === `t${stamp}nt1`.slice(0, 24));
  const social = ['like', 'comment', 'follow'];
  const countNotifs = async () => (await api('GET', '/api/notifications', { token: author.token })).json
    .filter((n) => social.includes(String(n.type).toLowerCase())).length;

  const base0 = await countNotifs();                          // conquistas já geradas ao criar a atividade
  assert.equal((await api('POST', `/api/feed/${post.id}/like`, { token: fan.token })).status, 200);
  assert.equal(await countNotifs(), base0 + 1);               // padrão: ligado

  const off = await api('PUT', '/api/users/me', { token: author.token, body: { notifSocial: false } });
  assert.equal(off.json.notifSocial, false);
  await api('POST', `/api/feed/${post.id}/comments`, { token: fan.token, body: { text: 'bom' } });
  await api('POST', `/api/users/${author.id}/toggle-follow`, { token: fan.token });
  assert.equal(await countNotifs(), base0 + 1);               // comentário e novo seguidor: nada novo

  await api('PUT', '/api/users/me', { token: author.token, body: { notifSocial: true } });
  await api('POST', `/api/feed/${post.id}/comments`, { token: fan.token, body: { text: 'de novo' } });
  assert.equal(await countNotifs(), base0 + 2);               // religou: volta a avisar

  assert.equal((await api('PUT', '/api/users/me', { token: author.token, body: { notifGoal: 'sim' } })).status, 400);
});

test('cadastro: data de nascimento estrita e mensagens de duplicidade específicas', opts, async () => {
  const mk = (over) => api('POST', '/api/auth/register', {
    body: { name: 'Teste Dup', username: `d${stamp}`.slice(0, 24), email: `d${stamp}@teste.local`, password: 'senha-forte-123', ...over },
  });
  assert.equal((await mk({ birthDate: '17/05/1990' })).status, 400);
  assert.equal((await mk({ birthDate: '1990-02-31' })).status, 400);
  assert.equal((await mk({ birthDate: '2999-01-01' })).status, 400);
  const ok = await mk({ birthDate: '1990-05-17', weight: 70, height: 170 });
  assert.equal(ok.status, 201, ok.text);
  created.push(`d${stamp}@teste.local`);
  const sameEmail = await mk({ username: 'outro_usuario_1' });
  assert.equal(sameEmail.status, 409);
  assert.match(sameEmail.json.error, /e-mail/i);
  const sameUser = await mk({ email: `outro${stamp}@teste.local` });
  assert.equal(sameUser.status, 409);
  assert.match(sameUser.json.error, /usu[aá]rio/i);
});

test('cadastro: se o token falhar, a conta NÃO fica criada (sem conta órfã)', opts, async () => {
  const email = `orfa${stamp}@teste.local`;
  await pool.query('ALTER TABLE refresh_tokens RENAME TO refresh_tokens_off');
  let r;
  try {
    r = await api('POST', '/api/auth/register', {
      body: { name: 'Conta Orfa', username: `orfa${stamp}`.slice(0, 24), email, password: 'senha-forte-123' },
    });
  } finally {
    await pool.query('ALTER TABLE refresh_tokens_off RENAME TO refresh_tokens');
  }
  assert.equal(r.status, 500);
  const found = await pool.query('SELECT 1 FROM users WHERE email = $1', [email]);
  assert.equal(found.rows.length, 0);
  // e a segunda tentativa, já com o banco certo, funciona
  const again = await api('POST', '/api/auth/register', {
    body: { name: 'Conta Orfa', username: `orfa${stamp}`.slice(0, 24), email, password: 'senha-forte-123' },
  });
  assert.equal(again.status, 201, again.text);
  created.push(email);
});

test('migração automática: banco vazio vira banco completo e rodar de novo não muda nada', opts, async () => {
  const { Pool, Client } = require('pg');
  const { runMigrations } = require('../src/db/autoMigrate');
  const name = `sv_fresh_${stamp}`;
  const url = new URL(process.env.DATABASE_URL);
  const admin = new Client({ connectionString: process.env.DATABASE_URL });
  await admin.connect();
  await admin.query(`CREATE DATABASE ${name}`);
  url.pathname = '/' + name;
  const fresh = new Pool({ connectionString: url.toString() });
  try {
    const logs = [];
    const first = await runMigrations(fresh, (m) => logs.push(m));
    assert.ok(first.includes('schema.sql'));
    assert.ok(first.includes('004_v5.sql'));
    const cols = await fresh.query("SELECT column_name FROM information_schema.columns WHERE table_name = 'users'");
    const names = cols.rows.map((c) => c.column_name);
    for (const c of ['email_verified', 'avatar_data', 'public_profile', 'notif_social', 'google_sub']) assert.ok(names.includes(c), c);
    const tables = await fresh.query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'");
    for (const t of ['refresh_tokens', 'challenges', 'schema_migrations']) assert.ok(tables.rows.some((r) => r.table_name === t), t);
    assert.deepEqual(await runMigrations(fresh, () => {}), []);       // idempotente
    // banco antigo (migrações aplicadas à mão, sem registro) também atualiza sem erro
    await fresh.query('DROP TABLE schema_migrations');
    const again = await runMigrations(fresh, () => {});
    assert.ok(again.includes('003_v4.sql'));
  } finally {
    await fresh.end();
    await admin.query(`DROP DATABASE ${name}`);
    await admin.end();
  }
});

test('servidor sobe mesmo com JWT_SECRET curto (só avisa)', opts, async () => {
  const { spawnSync } = require('node:child_process');
  const r = spawnSync(process.execPath, ['-e',
    "process.env.JWT_SECRET='curto'; require('./src/server'); console.log('SUBIU'); setTimeout(()=>process.exit(0),100)"],
  { cwd: require('path').join(__dirname, '..'), env: { ...process.env, JWT_SECRET: 'curto' }, encoding: 'utf8' });
  assert.match(r.stdout, /SUBIU/);
  assert.match(r.stderr, /JWT_SECRET curto/);
});

test('health informa a versão do banco', opts, async () => {
  const h = await api('GET', '/api/health');
  assert.equal(h.status, 200);
  assert.equal(h.json.ok, true);
  assert.ok('schema' in h.json);
});

test('limitador: bloqueia depois do máximo e libera depois da janela', () => {
  const { rateLimit } = require('../src/middleware/rateLimit');
  const lim = rateLimit({ windowMs: 200, max: 3, key: (r) => r.k });
  const mk = () => {
    const res = { code: 200, headers: {}, set(k, v) { this.headers[k] = v; }, status(c) { this.code = c; return this; }, json() { return this; } };
    return res;
  };
  const hit = (k) => { const res = mk(); let passed = false; lim({ k }, res, () => { passed = true; }); return { passed, res }; };
  assert.equal(hit('a').passed, true);
  assert.equal(hit('a').passed, true);
  assert.equal(hit('a').passed, true);
  const blocked = hit('a');
  assert.equal(blocked.passed, false);
  assert.equal(blocked.res.code, 429);
  assert.ok(blocked.res.headers['Retry-After']);
  assert.equal(hit('b').passed, true); // outra chave não é afetada
  return new Promise((ok) => setTimeout(() => { assert.equal(hit('a').passed, true); ok(); }, 260));
});
