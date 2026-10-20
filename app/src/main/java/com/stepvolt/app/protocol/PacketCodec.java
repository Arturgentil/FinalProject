package com.stepvolt.app.protocol;

import java.nio.charset.Charset;

/**
 * Protocolo v2 entre o tênis e o app.
 *
 * Formato de cada linha enviada pelo tênis:
 *   {"v":2,"seq":41,"bpm":82,...}*3F\n
 * ou seja: um objeto JSON, um asterisco e dois dígitos hexadecimais com o
 * checksum (XOR de todos os bytes do JSON). O app confere o checksum antes de
 * aceitar o pacote — pacote corrompido é descartado e contado, nunca vira dado.
 *
 * Pacotes sem "*XX" (firmware v1) continuam aceitos, para não deixar quem já
 * tem o tênis gravado com a versão antiga sem app.
 *
 * Comandos (app -> tênis) seguem a mesma regra:  CMD:CALIBRATE:7*2A
 * e o tênis responde com um pacote {"ack":"CALIBRATE","id":7,"ok":1}.
 */
public final class PacketCodec {

    private PacketCodec() {}

    public static final int PROTOCOL_VERSION = 2;

    /** Resultado da leitura de uma linha. */
    public static final class Decoded {
        public final String json;
        public final boolean checksummed;
        public final boolean valid;
        public final String error;

        Decoded(String json, boolean checksummed, boolean valid, String error) {
            this.json = json;
            this.checksummed = checksummed;
            this.valid = valid;
            this.error = error;
        }
    }

    public static int checksum(String payload) {
        byte[] bytes = payload.getBytes(Charset.forName("UTF-8"));
        int x = 0;
        for (byte b : bytes) x ^= (b & 0xFF);
        return x & 0xFF;
    }

    public static String hex2(int v) {
        String s = Integer.toHexString(v & 0xFF).toUpperCase();
        return s.length() == 1 ? "0" + s : s;
    }

    /** Anexa o checksum a um payload. */
    public static String seal(String payload) {
        return payload + "*" + hex2(checksum(payload));
    }

    /** Lê uma linha completa (sem o \n final). */
    public static Decoded decode(String line) {
        if (line == null) return new Decoded(null, false, false, "linha vazia");
        String s = line.trim();
        if (s.isEmpty()) return new Decoded(null, false, false, "linha vazia");

        int star = s.lastIndexOf('*');
        int closing = s.lastIndexOf('}');
        if (star > closing && closing >= 0) {
            String json = s.substring(0, star);
            String hex = s.substring(star + 1).trim();
            if (hex.length() != 2) return new Decoded(json, true, false, "checksum malformado");
            int expected;
            try {
                expected = Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                return new Decoded(json, true, false, "checksum malformado");
            }
            if (expected != checksum(json)) {
                return new Decoded(json, true, false, "checksum não confere");
            }
            return new Decoded(json, true, true, null);
        }
        if (closing >= 0 && s.startsWith("{")) {
            String json = s.substring(0, closing + 1);
            if (json.contains("\"v\":2")) {
                return new Decoded(json, false, false, "checksum ausente no protocolo v2");
            }
            // v1: sem checksum, só confere se o objeto está fechado.
            return new Decoded(json, false, true, null);
        }
        return new Decoded(s, false, false, "não é um objeto JSON");
    }

    /** Monta um comando para o tênis: CMD:NOME[:ARG]:ID*XX */
    public static String encodeCommand(String name, String arg, int id) {
        StringBuilder sb = new StringBuilder("CMD:").append(name.toUpperCase());
        if (arg != null && !arg.isEmpty()) sb.append(':').append(arg);
        sb.append(':').append(id);
        return seal(sb.toString());
    }
}
