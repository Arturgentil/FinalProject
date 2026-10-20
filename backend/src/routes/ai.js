const express = require('express');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

const DISCLAIMER = 'Esta leitura é apenas informativa, feita a partir dos números do seu tênis. '
  + 'Ela não é um diagnóstico médico. Em caso de dúvida sobre sua saúde, procure um profissional.';

/**
 * Rota provedor-agnóstica: se AI_API_KEY estiver configurada, chama o
 * endpoint de chat compatível com a API da Anthropic ou da OpenAI (definido
 * por AI_PROVIDER) e usa o texto para montar os cartões. Sem chave, devolve a
 * mesma leitura por regras que o app já sabe fazer sozinho — o app nunca fica
 * sem resposta por falta de configuração no servidor.
 */
router.post('/insights', requireAuth, async (req, res) => {
  const body = req.body || {};
  const { period, steps, energy, sampleCount, metrics } = body;

  if (!sampleCount || sampleCount <= 0) {
    return res.json({
      source: 'rules',
      summary: '',
      insights: [],
      disclaimer: DISCLAIMER,
    });
  }

  const apiKey = process.env.AI_API_KEY;
  if (apiKey) {
    try {
      const result = await callProvider(apiKey, body);
      if (result) {
        return res.json({ ...result, disclaimer: DISCLAIMER });
      }
    } catch (err) {
      console.error('Provedor de IA falhou, usando leitura local:', err.message);
      // Cai para a leitura por regras abaixo — a pessoa não fica sem resposta
      // só porque o provedor externo está fora do ar.
    }
  }

  res.json({
    source: 'rules',
    summary: ruleSummary(period, steps, energy, sampleCount),
    insights: ruleInsights(metrics || {}),
    disclaimer: DISCLAIMER,
  });
});

async function callProvider(apiKey, body) {
  const provider = (process.env.AI_PROVIDER || 'anthropic').toLowerCase();
  const model = process.env.AI_MODEL || (provider === 'openai' ? 'gpt-4o-mini' : 'claude-sonnet-4-6');
  const prompt = buildPrompt(body);

  let text;
  if (provider === 'openai') {
    const resp = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({
        model,
        messages: [{ role: 'user', content: prompt }],
        max_tokens: 700,
        response_format: { type: 'json_object' },
      }),
    });
    if (!resp.ok) throw new Error(`OpenAI respondeu ${resp.status}`);
    const data = await resp.json();
    text = data.choices?.[0]?.message?.content;
  } else {
    const resp = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': apiKey,
        'anthropic-version': '2023-06-01',
      },
      body: JSON.stringify({
        model,
        max_tokens: 700,
        messages: [{ role: 'user', content: prompt }],
      }),
    });
    if (!resp.ok) throw new Error(`Anthropic respondeu ${resp.status}`);
    const data = await resp.json();
    text = data.content?.map((c) => c.text || '').join('');
  }

  if (!text) return null;
  const clean = text.replace(/```json|```/g, '').trim();
  const parsed = JSON.parse(clean);
  return {
    source: 'model',
    model,
    summary: parsed.summary || '',
    insights: Array.isArray(parsed.insights) ? parsed.insights.slice(0, 6) : [],
  };
}

function buildPrompt(body) {
  return `Você lê dados de sensores de um tênis com gerador de energia (passos, energia, e opcionalmente `
    + `batimentos, oxigenação e temperatura de quem está usando). Responda SOMENTE com um JSON no formato `
    + `{"summary": string, "insights": [{"title": string, "body": string, "tone": "info"|"positive"|"attention"}]}`
    + `, em português do Brasil, com no máximo 4 cartões. Descreva o que os números mostram e o que costuma `
    + `explicá-los. NUNCA dê diagnóstico médico, não recomende medicação nem afirme causa clínica — no máximo `
    + `sugira procurar um profissional se algo estiver bem fora do habitual. Dados do período (${body.period}): `
    + JSON.stringify({ steps: body.steps, energy: body.energy, sampleCount: body.sampleCount, metrics: body.metrics });
}

// ------------------------------------------------------------------
// Leitura por regras — mesma lógica de health/Insights.java, em JS,
// para o servidor responder algo útil mesmo sem provedor configurado.
// ------------------------------------------------------------------

function ruleSummary(period, steps, energy, sampleCount) {
  const parts = [];
  if (steps) parts.push(`${Math.round(steps).toLocaleString('pt-BR')} passos`);
  if (energy) parts.push(`${energy.toFixed(1)} mWh gerados`);
  const when = period || 'no período';
  return parts.length
    ? `Em ${when}, o tênis registrou ${parts.join(' e ')}, a partir de ${sampleCount} medições.`
    : `${sampleCount} medições registradas ${when}.`;
}

function ruleInsights(metrics) {
  const cards = [];
  const labels = {
    bpm: { name: 'Batimentos', unit: 'bpm' },
    spo2: { name: 'Oxigenação', unit: '%' },
    temp: { name: 'Temperatura', unit: '°C' },
  };

  for (const key of Object.keys(labels)) {
    const st = metrics[key];
    if (!st || !st.count) continue;
    const { name, unit } = labels[key];
    let tone = 'info';
    let trend = '';
    if (Math.abs(st.slopePerMinute || 0) > 0.0005 && st.count > 20) {
      trend = st.slopePerMinute > 0 ? ' A tendência do período é de alta.' : ' A tendência do período é de queda.';
    }
    if (key === 'spo2' && st.min < 92) tone = 'attention';
    if (key === 'bpm' && (st.max > 180 || st.min < 40)) tone = 'attention';

    cards.push({
      title: name,
      body: `Média de ${st.avg.toFixed(1)} ${unit}, variando entre ${st.min.toFixed(1)} e `
        + `${st.max.toFixed(1)} ${unit} em ${st.count} leituras.${trend}`,
      tone,
    });
  }

  if (cards.length === 0) {
    cards.push({
      title: 'Passos e energia',
      body: 'Este período teve apenas dados de passos e energia — sem sinais vitais para detalhar.',
      tone: 'info',
    });
  }
  return cards;
}

module.exports = router;
