package com.stepvolt.app.net;

import java.util.ArrayList;
import java.util.List;

/**
 * Resposta de /api/ai/insights.
 *
 * O formato é o mesmo com ou sem provedor externo configurado: o servidor
 * sempre devolve cartões prontos para desenhar, mais a origem da análise, para
 * o app poder dizer com honestidade de onde veio o texto.
 */
public class AiResponse {

    public static class Insight {
        public String title;
        public String body;
        /** "info", "positive", "attention" — decide só a cor da barra lateral. */
        public String tone;
    }

    /** "model" quando veio de um provedor externo, "rules" quando é a leitura local do servidor. */
    public String source;

    /** Nome do modelo, quando houver. Apenas informativo. */
    public String model;

    public String summary;

    public List<Insight> insights = new ArrayList<>();

    /** Aviso obrigatório devolvido pelo servidor; o app mostra o próprio se vier vazio. */
    public String disclaimer;

    public boolean hasContent() {
        return (summary != null && !summary.isEmpty()) || (insights != null && !insights.isEmpty());
    }
}
