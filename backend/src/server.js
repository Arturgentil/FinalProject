require('dotenv').config();
const express = require('express');
const cors = require('cors');
const morgan = require('morgan');

const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/users');
const feedRoutes = require('./routes/feed');
const notificationRoutes = require('./routes/notifications');
const goalRoutes = require('./routes/goals');
const achievementRoutes = require('./routes/achievements');
const activityRoutes = require('./routes/activities');
const deviceRoutes = require('./routes/devices');
const telemetryRoutes = require('./routes/telemetry');
const alertRoutes = require('./routes/alerts');
const aiRoutes = require('./routes/ai');
const { challengesRouter, leaderboardRouter } = require('./routes/challenges');
const { securityHeaders } = require('./middleware/security');
const { rateLimit } = require('./middleware/rateLimit');
const { runMigrations, currentVersion } = require('./db/autoMigrate');
const { pool } = require('./db/pool');

const app = express();
// Atrás de proxy (Render, Nginx...) o IP real vem no cabeçalho; sem isto o
// limitador veria todos como um só.
if (process.env.TRUST_PROXY !== '0') app.set('trust proxy', 1);
app.use(securityHeaders);
app.use(cors());
// 2 MB cobre o maior lote de telemetria (5.000 amostras) com folga.
app.use(express.json({ limit: '2mb' }));
app.use(morgan('dev'));

if (!process.env.JWT_SECRET) {
  console.error('JWT_SECRET ausente. Defina no .env (ou nas variáveis do Render).');
  process.exit(1);
}
if (process.env.JWT_SECRET.length < 24) {
  // Só avisa: derrubar o servidor por isso deixava o app inteiro fora do ar.
  console.warn('AVISO: JWT_SECRET curto (menos de 24 caracteres). Troque por um valor longo e aleatório.');
}

// Limite geral por IP: protege todas as rotas de abuso simples.
app.use('/api', rateLimit({ windowMs: 60_000, max: Number(process.env.RATE_LIMIT_PER_MIN) || 300 }));


let migrationError = null;

// Também serve para "acordar" o servidor: o app chama esta rota ao abrir, antes de a pessoa
// terminar de preencher o cadastro (no plano gratuito do Render o primeiro acesso demora).
app.get('/api/health', async (req, res) => {
  res.json({ ok: !migrationError, schema: await currentVersion(pool), migrationError });
});

app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/feed', feedRoutes);
app.use('/api/notifications', notificationRoutes);
app.use('/api/goals', goalRoutes);
app.use('/api/achievements', achievementRoutes);
app.use('/api/activities', activityRoutes);
app.use('/api/devices', deviceRoutes);
app.use('/api/telemetry', telemetryRoutes);
app.use('/api/alerts', alertRoutes);
app.use('/api/ai', aiRoutes);
app.use('/api/challenges', challengesRouter);
app.use('/api/leaderboard', leaderboardRouter);

app.use((req, res) => res.status(404).json({ error: 'Rota não encontrada.' }));

// JSON malformado e outros erros viram resposta limpa, sem expor detalhes internos.
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  if (err && err.type === 'entity.parse.failed') return res.status(400).json({ error: 'JSON inválido.' });
  if (err && err.type === 'entity.too.large') return res.status(413).json({ error: 'Requisição grande demais.' });
  console.error(err);
  res.status(500).json({ error: 'Erro interno.' });
});

module.exports = app;

if (require.main === module) {
  (async () => {
    if (process.env.AUTO_MIGRATE !== '0') {
      try {
        const applied = await runMigrations(pool);
        if (applied.length) console.log('Banco atualizado:', applied.join(', '));
      } catch (err) {
        migrationError = err.message;
        console.error('FALHA AO ATUALIZAR O BANCO:', err.message);
      }
    }
    const PORT = process.env.PORT || 3000;
    app.listen(PORT, '0.0.0.0', () => {
      console.log(`StepVolt API rodando em http://0.0.0.0:${PORT}`);
    });
  })();
}
