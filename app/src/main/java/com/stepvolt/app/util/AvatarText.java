package com.stepvolt.app.util;

/** Texto do avatar sem foto e resolução de endereço. Sem Android, para poder testar. */
public final class AvatarText {

    private AvatarText() {}

    /** "Ana Souza" -> "AS"; "ana" -> "A"; vazio -> "" (o desenho cai no ícone de pessoa). */
    public static String initials(String name) {
        if (name == null) return "";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            int cp = p.codePointAt(0);
            if (!Character.isLetterOrDigit(cp)) continue;
            sb.appendCodePoint(Character.toUpperCase(cp));
            break;
        }
        // Segunda letra: a primeira do ÚLTIMO nome (ignora nomes de uma palavra só).
        if (parts.length > 1 && sb.length() > 0) {
            String last = parts[parts.length - 1];
            if (!last.isEmpty() && Character.isLetterOrDigit(last.codePointAt(0))) {
                sb.appendCodePoint(Character.toUpperCase(last.codePointAt(0)));
            }
        }
        return sb.toString();
    }

    /** Escolhe sempre a mesma cor para o mesmo nome (assim o avatar não "muda de cor" a cada abertura). */
    public static int colorIndex(String name, int paletteSize) {
        if (name == null || name.isEmpty() || paletteSize <= 0) return 0;
        int h = 17;
        for (int i = 0; i < name.length(); i++) h = h * 31 + Character.toLowerCase(name.charAt(i));
        return Math.abs(h % paletteSize);
    }

    /**
     * O servidor guarda a foto como caminho relativo ("/api/users/7/avatar?v=...").
     * Junta com o endereço da API; endereços completos passam sem mudança.
     */
    public static String resolveUrl(String baseUrl, String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.isEmpty()) return null;
        if (u.startsWith("http://") || u.startsWith("https://")) return u;
        if (!u.startsWith("/")) return u;
        if (baseUrl == null || baseUrl.trim().isEmpty()) return null;
        String b = baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b + u;
    }
}
