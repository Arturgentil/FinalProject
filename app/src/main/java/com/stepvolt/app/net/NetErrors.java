package com.stepvolt.app.net;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

/**
 * Traduz uma falha de rede numa mensagem que diz o que fazer. "Não foi possível
 * conectar" para tudo levava a pessoa a mexer no endereço do servidor quando o
 * problema era só o servidor acordando. Sem Android, para poder testar.
 */
public final class NetErrors {

    private NetErrors() {}

    public static String describe(Throwable t, boolean online, String baseUrl) {
        if (!online) {
            return "Sem internet. Conecte-se ao Wi-Fi ou aos dados m\u00f3veis e tente de novo.";
        }
        Throwable root = t;
        while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();

        if (t instanceof SocketTimeoutException || root instanceof SocketTimeoutException) {
            return "O servidor demorou para responder. No primeiro acesso do dia ele pode levar at\u00e9 1 minuto "
                    + "para acordar. Aguarde um pouco e toque de novo.";
        }
        if (root instanceof UnknownHostException) {
            return "N\u00e3o encontrei o servidor" + hostHint(baseUrl) + ". Confira o endere\u00e7o em Ajustes > Servidor.";
        }
        if (root instanceof SSLException) {
            return "A conex\u00e3o segura com o servidor falhou. Confira se o endere\u00e7o come\u00e7a com https:// "
                    + "e se a data e a hora do celular est\u00e3o certas.";
        }
        if (root instanceof ConnectException) {
            return "N\u00e3o consegui conectar ao servidor" + hostHint(baseUrl) + ". Ele pode estar fora do ar ou "
                    + "o endere\u00e7o em Ajustes > Servidor est\u00e1 errado.";
        }
        String detail = root == null ? "" : root.getClass().getSimpleName();
        return "Falha de conex\u00e3o com o servidor" + (detail.isEmpty() ? "" : " (" + detail + ")") + ". Tente de novo.";
    }

    /** true para falhas que costumam passar sozinhas se esperar um pouco. */
    public static boolean isTransient(Throwable t) {
        Throwable root = t;
        while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();
        return t instanceof SocketTimeoutException || root instanceof SocketTimeoutException
                || root instanceof ConnectException;
    }

    /** Respostas HTTP sem corpo JSON do nosso servidor: quem respondeu foi o proxy. */
    public static String describeHttp(int code) {
        switch (code) {
            case 502:
            case 503:
            case 504:
                return "O servidor est\u00e1 iniciando (comum no primeiro acesso do dia). Aguarde cerca de 30 segundos e tente de novo.";
            case 429:
                return "Muitas tentativas seguidas. Aguarde um pouco e tente de novo.";
            case 413:
                return "O arquivo \u00e9 grande demais.";
            case 404:
                return "O servidor n\u00e3o reconheceu este pedido. O app e o servidor podem estar em vers\u00f5es diferentes.";
            default:
                return "Erro inesperado (c\u00f3digo " + code + ").";
        }
    }

    private static String hostHint(String baseUrl) {
        if (baseUrl == null) return "";
        String h = baseUrl.replaceFirst("^https?://", "");
        int slash = h.indexOf('/');
        if (slash > 0) h = h.substring(0, slash);
        return h.isEmpty() ? "" : " (" + h + ")";
    }
}
