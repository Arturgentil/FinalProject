// Limitador de requisições em memória (janela deslizante simples).
//
// Vale por processo: se rodar mais de uma instância do servidor, cada uma conta
// separado, e reiniciar zera os contadores. Para uma instância só (o caso
// típico deste projeto) basta; para várias, troque o Map por Redis.

function rateLimit({ windowMs, max, key, message }) {
  const hits = new Map();

  const timer = setInterval(() => {
    const cutoff = Date.now() - windowMs;
    for (const [k, list] of hits) {
      const fresh = list.filter((t) => t > cutoff);
      if (fresh.length === 0) hits.delete(k); else hits.set(k, fresh);
    }
  }, Math.min(windowMs, 60_000));
  timer.unref();

  return function limiter(req, res, next) {
    const k = (key ? key(req) : req.ip) || 'anon';
    const now = Date.now();
    const list = (hits.get(k) || []).filter((t) => t > now - windowMs);
    if (list.length >= max) {
      const retry = Math.max(1, Math.ceil((list[0] + windowMs - now) / 1000));
      res.set('Retry-After', String(retry));
      return res.status(429).json({ error: message || 'Muitas tentativas. Tente de novo em instantes.' });
    }
    list.push(now);
    hits.set(k, list);
    next();
  };
}

module.exports = { rateLimit };
