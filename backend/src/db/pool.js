const { Pool } = require('pg');

const useSSL = (process.env.DATABASE_URL || '').includes('sslmode=require');

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: useSSL ? { rejectUnauthorized: false } : false,
});

pool.on('error', (err) => {
  console.error('Erro inesperado no pool do Postgres', err);
});

module.exports = { pool };
