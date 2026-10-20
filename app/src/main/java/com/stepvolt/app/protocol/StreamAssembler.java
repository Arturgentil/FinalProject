package com.stepvolt.app.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Junta os pedaços que chegam por notificação BLE e devolve pacotes completos.
 * Uma notificação pode trazer meio pacote, um pacote ou dois de uma vez.
 *
 * - v1: o pacote termina no "}" que fecha o objeto.
 * - v2 (objeto contém "v":2): o pacote termina em "}*XX" — só é entregue
 *   quando os dois dígitos do checksum chegaram.
 */
public final class StreamAssembler {

    private static final int MAX_BUFFER = 8192;
    private final StringBuilder buffer = new StringBuilder();
    private int overflows = 0;

    public synchronized List<String> feed(String chunk) {
        List<String> out = new ArrayList<>();
        if (chunk == null || chunk.isEmpty()) return out;
        buffer.append(chunk);
        if (buffer.length() > MAX_BUFFER) {
            buffer.setLength(0);
            overflows++;
            return out;
        }

        while (true) {
            int start = buffer.indexOf("{");
            if (start < 0) { buffer.setLength(0); break; }
            if (start > 0) buffer.delete(0, start);

            int end = findObjectEnd(buffer);
            if (end < 0) break; // objeto ainda incompleto

            String object = buffer.substring(0, end + 1);
            int cut = end + 1;
            if (isV2(object)) {
                // Precisa de "*" + 2 dígitos depois do "}".
                if (buffer.length() < cut + 3) break;
                if (buffer.charAt(cut) == '*') cut += 3;
                // Se não vier "*", entrega assim mesmo: o PacketCodec rejeita
                // o v2 sem checksum e o app conta como pacote inválido.
            }
            out.add(buffer.substring(0, cut));
            buffer.delete(0, cut);
        }
        return out;
    }

    public synchronized int overflows() { return overflows; }

    public synchronized void reset() { buffer.setLength(0); }

    static boolean isV2(String object) {
        return object.contains("\"v\":2");
    }

    /** Posição do "}" que fecha o objeto que começa no índice 0, ou -1. */
    private static int findObjectEnd(CharSequence s) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
