// Aplica o schema e as migrações pendentes quando o servidor sobe.
//
// Por que existe: a V4/V5 do servidor depende de tabelas e colunas novas. Quando
// o código novo ia para o ar sem alguém rodar `node src/db/migrate.js`, o
// cadastro e o login falhavam (a tabela de tokens não existia) — e pior: o
// usuário era criado antes do erro, então a segunda tentativa dizia que o
// e-mail já estava cadastrado. Agora o banco se atualiza sozinho.
//
// As migrações são idempotentes (IF NOT EXISTS) e ficam registradas em
// schema_migrations; um bloqueio do Postgres evita duas instâncias migrando ao mesmo tempo.
const fs = require('fs');
const path = require('path');

const LOCK_ID = 727171;

async function runMigrations(pool, log = console.log) {
  const client = await pool.connect();
  const applied = [];
  try {
    await client.query('SELECT pg_advisory_lock($1)', [LOCK_ID]);
    await client.query(`CREATE TABLE IF NOT EXISTS schema_migrations (
      name TEXT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())`);

    const hasUsers = (await client.query("SELECT to_regclass('public.users') AS t")).rows[0].t;
    if (!hasUsers) {
      log('Banco vazio: aplicando schema.sql...');
      await client.query(fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8'));
      applied.push('schema.sql');
    }

    const dir = path.join(__dirname, 'migrations');
    const files = fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => f.endsWith('.sql')).sort() : [];
    const done = new Set((await client.query('SELECT name FROM schema_migrations')).rows.map((r) => r.name));
    for (const f of files) {
      if (done.has(f)) continue;
      log(`Aplicando migração ${f}...`);
      await client.query('BEGIN');
      try {
        await client.query(fs.readFileSync(path.join(dir, f), 'utf8'));
        await client.query('INSERT INTO schema_migrations (name) VALUES ($1)', [f]);
        await client.query('COMMIT');
        applied.push(f);
      } catch (err) {
        await client.query('ROLLBACK').catch(() => {});
        throw new Error(`migração ${f} falhou: ${err.message}`);
      }
    }
    return applied;
  } finally {
    await client.query('SELECT pg_advisory_unlock($1)', [LOCK_ID]).catch(() => {});
    client.release();
  }
}

async function currentVersion(pool) {
  try {
    const r = await pool.query('SELECT name FROM schema_migrations ORDER BY name DESC LIMIT 1');
    return r.rows[0] ? r.rows[0].name : null;
  } catch (e) {
    return null;
  }
}

module.exports = { runMigrations, currentVersion };
