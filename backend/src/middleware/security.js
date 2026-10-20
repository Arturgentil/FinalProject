// Cabeçalhos de segurança básicos (o essencial do que o helmet faz, sem a dependência).
function securityHeaders(req, res, next) {
  res.set('X-Content-Type-Options', 'nosniff');
  res.set('X-Frame-Options', 'DENY');
  res.set('Referrer-Policy', 'no-referrer');
  res.set('Cross-Origin-Resource-Policy', 'same-site');
  res.removeHeader('X-Powered-By');
  next();
}

module.exports = { securityHeaders };
