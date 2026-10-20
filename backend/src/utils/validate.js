// Validação de entrada sem dependências. Cada função devolve null quando está
// tudo certo, ou uma mensagem em português pronta para ir ao usuário.

const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$/;
const USERNAME_RE = /^[a-zA-Z0-9_.]{3,24}$/;

function str(v, { min = 0, max = 255 } = {}) {
  return typeof v === 'string' && v.trim().length >= min && v.length <= max;
}

function int(v, { min = 0, max = Number.MAX_SAFE_INTEGER } = {}) {
  return Number.isInteger(v) && v >= min && v <= max;
}

function num(v, { min = 0, max = Number.MAX_VALUE } = {}) {
  return typeof v === 'number' && Number.isFinite(v) && v >= min && v <= max;
}

function isEmail(v) {
  return typeof v === 'string' && v.length <= 320 && EMAIL_RE.test(v.trim());
}

function birthDateProblem(v) {
  if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(v)) return 'Data de nascimento no formato AAAA-MM-DD.';
  const d = new Date(v + 'T00:00:00Z');
  if (Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== v) return 'Data de nascimento inválida.';
  if (Number(v.slice(0, 4)) < 1900 || d > new Date()) return 'Data de nascimento fora do intervalo possível.';
  return null;
}

function validateRegister(b) {
  if (!str(b.name, { min: 2, max: 80 })) return 'Informe seu nome (2 a 80 letras).';
  if (typeof b.username !== 'string' || !USERNAME_RE.test(b.username)) {
    return 'O usuário deve ter de 3 a 24 caracteres: letras, números, ponto ou sublinhado.';
  }
  if (!isEmail(b.email)) return 'Informe um e-mail válido.';
  if (!str(b.password, { min: 8, max: 128 })) return 'A senha deve ter de 8 a 128 caracteres.';
  if (b.weight != null && b.weight !== '' && !num(Number(b.weight), { min: 0, max: 500 })) return 'Peso inválido.';
  if (b.height != null && b.height !== '' && !num(Number(b.height), { min: 0, max: 300 })) return 'Altura inválida.';
  if (b.birthDate != null && b.birthDate !== '') {
    const p = birthDateProblem(b.birthDate);
    if (p) return p;
  }
  return null;
}

function numberList(list, { maxLen = 2000, min = 0, max = 1000 } = {}) {
  if (list == null) return true;
  if (!Array.isArray(list) || list.length > maxLen) return false;
  return list.every((x) => typeof x === 'number' && Number.isFinite(x) && x >= min && x <= max);
}

function validateActivity(b) {
  if (!num(Number(b.steps), { min: 0, max: 10_000_000 })) return 'steps inválido.';
  if (!num(Number(b.energy), { min: 0, max: 10_000_000 })) return 'energy inválido.';
  if (b.duration != null && !num(Number(b.duration), { min: 0, max: 24 * 60 * 3 })) return 'duration inválido.';
  if (b.distance != null && !num(Number(b.distance), { min: 0, max: 2000 })) return 'distance inválido.';
  if (b.bpmAvg != null && !num(Number(b.bpmAvg), { min: 0, max: 260 })) return 'bpmAvg inválido.';
  if (!numberList(b.bpmSamples, { max: 260 })) return 'bpmSamples inválido.';
  if (!numberList(b.spo2Samples, { max: 100 })) return 'spo2Samples inválido.';
  if (b.date != null && !str(String(b.date), { max: 20 })) return 'date inválido.';
  if (b.kind != null && !['walk', 'run', 'hike', 'other'].includes(b.kind)) return 'kind inválido.';
  if (b.elevationGain != null && !num(Number(b.elevationGain), { min: 0, max: 20000 })) return 'elevationGain inválido.';
  return null;
}

const COMMENT_POLICIES = ['all', 'followers', 'none'];

// Atualização de perfil: só valida o que veio; o que não veio fica como está.
function validateProfileUpdate(b) {
  if (b.name !== undefined && !str(b.name, { min: 2, max: 80 })) return 'O nome deve ter de 2 a 80 caracteres.';
  if (b.username !== undefined && (typeof b.username !== 'string' || !USERNAME_RE.test(b.username))) {
    return 'O usuário deve ter de 3 a 24 caracteres: letras, números, ponto ou sublinhado.';
  }
  if (b.birthDate !== undefined && b.birthDate !== '') {
    const p = birthDateProblem(b.birthDate);
    if (p) return p;
  }
  if (b.weight !== undefined && !num(b.weight, { min: 0, max: 500 })) return 'Peso inválido (0 a 500 kg).';
  if (b.height !== undefined && !num(b.height, { min: 0, max: 300 })) return 'Altura inválida (0 a 300 cm).';
  if (b.bio !== undefined && (typeof b.bio !== 'string' || b.bio.length > 280)) return 'A biografia pode ter até 280 caracteres.';
  if (b.publicProfile !== undefined && typeof b.publicProfile !== 'boolean') return 'publicProfile inválido.';
  if (b.autoShare !== undefined && typeof b.autoShare !== 'boolean') return 'autoShare inválido.';
  if (b.commentPolicy !== undefined && !COMMENT_POLICIES.includes(b.commentPolicy)) return 'commentPolicy inválido.';
  for (const k of ['notifGoal', 'notifRecord', 'notifSocial']) {
    if (b[k] !== undefined && typeof b[k] !== 'boolean') return `${k} inválido.`;
  }
  return null;
}

const MAX_AVATAR_BYTES = 400 * 1024;

// Confere o formato pelos primeiros bytes (não confia no que o cliente diz).
function sniffImage(buf) {
  if (buf.length > 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'image/jpeg';
  if (buf.length > 8 && buf.slice(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'image/png';
  if (buf.length > 12 && buf.slice(0, 4).toString('ascii') === 'RIFF' && buf.slice(8, 12).toString('ascii') === 'WEBP') return 'image/webp';
  return null;
}

const METRICS = ['steps', 'energy', 'distance'];

function validateChallenge(b) {
  if (!str(b.title, { min: 3, max: 60 })) return 'O nome do desafio deve ter de 3 a 60 caracteres.';
  if (!METRICS.includes(b.metric)) return 'Escolha a medida: passos, energia ou distância.';
  if (!int(Number(b.days), { min: 1, max: 90 })) return 'A duração deve ser de 1 a 90 dias.';
  if (b.targetValue != null && !num(Number(b.targetValue), { min: 0, max: 100_000_000 })) return 'Meta inválida.';
  return null;
}

module.exports = { isEmail, str, int, num, validateRegister, validateActivity, validateChallenge, validateProfileUpdate, sniffImage, MAX_AVATAR_BYTES, COMMENT_POLICIES, METRICS };
