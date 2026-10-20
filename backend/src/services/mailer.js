// Envio de e-mail. Sem SMTP_URL configurada, o link vai para o log do servidor
// (útil em desenvolvimento) e a função devolve { delivered: false } — o app
// nunca finge que o e-mail saiu.
let transporter = null;
let tried = false;

function getTransporter() {
  if (tried) return transporter;
  tried = true;
  if (!process.env.SMTP_URL) return null;
  try {
    // nodemailer é carregado só quando há SMTP configurada.
    // eslint-disable-next-line global-require
    transporter = require('nodemailer').createTransport(process.env.SMTP_URL);
  } catch (err) {
    console.error('SMTP_URL definida, mas o nodemailer não pôde ser carregado:', err.message);
  }
  return transporter;
}

async function sendMail({ to, subject, text, html }) {
  const t = getTransporter();
  if (!t) {
    console.log(`[mailer] SMTP não configurada. E-mail para ${to}: ${subject}\n${text}`);
    return { delivered: false };
  }
  await t.sendMail({ from: process.env.MAIL_FROM || 'StepVolt <nao-responda@stepvolt.local>', to, subject, text, html });
  return { delivered: true };
}

function publicBaseUrl(req) {
  return (process.env.PUBLIC_URL || `${req.protocol}://${req.get('host')}`).replace(/\/$/, '');
}

async function sendVerificationEmail(req, user, rawToken) {
  const link = `${publicBaseUrl(req)}/api/auth/verify-email?token=${encodeURIComponent(rawToken)}`;
  return sendMail({
    to: user.email,
    subject: 'Confirme seu e-mail no StepVolt',
    text: `Olá, ${user.name}!\n\nConfirme seu e-mail abrindo este link (vale por 24 horas):\n${link}\n\nSe não foi você, ignore esta mensagem.`,
    html: `<p>Olá, ${String(user.name).replace(/[<>&]/g, '')}!</p><p><a href="${link}">Confirmar meu e-mail</a> (vale por 24 horas).</p><p>Se não foi você, ignore esta mensagem.</p>`,
  });
}

module.exports = { sendMail, sendVerificationEmail };
