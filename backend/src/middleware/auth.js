const jwt = require('jsonwebtoken');

function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ error: 'Token ausente. Faça login novamente.' });
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET);
    if (!Number.isInteger(payload.userId)) throw new Error('payload inválido');
    req.userId = payload.userId;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Token inválido ou expirado.' });
  }
}

module.exports = { requireAuth };
