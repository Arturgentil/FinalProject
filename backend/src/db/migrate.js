require('dotenv').config();
const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');

async function main() {
  const pool = new Pool({ connectionString: process.env.DATABASE_URL });
  try {
    const schema = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');
    console.log('Aplicando schema.sql...');
    await pool.query(schema);

    // Aplica todas as migrações da pasta, em ordem alfabética (002_..., 003_...).
    const dir = path.join(__dirname, 'migrations');
    const files = fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => f.endsWith('.sql')).sort() : [];
    for (const f of files) {
      console.log(`Aplicando migrations/${f}...`);
      await pool.query(fs.readFileSync(path.join(dir, f), 'utf8'));
    }

    const seed = fs.readFileSync(path.join(__dirname, 'seed.sql'), 'utf8');
    console.log('Aplicando seed.sql...');
    await pool.query(seed);

    console.log('Pronto! Banco configurado.');
  } catch (err) {
    console.error('Erro ao migrar:', err.message);
    process.exitCode = 1;
  } finally {
    await pool.end();
  }
}

main();
