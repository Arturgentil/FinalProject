package com.stepvolt.app.util;

import java.util.Calendar;
import java.util.regex.Pattern;

/** Regras dos campos do perfil. As mesmas do servidor, para o erro aparecer antes de enviar. Sem Android. */
public final class ProfileRules {

    private ProfileRules() {}

    public static final int BIO_MAX = 280;
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_.]{3,24}$");

    /** null = tudo certo; senão, a mensagem. */
    public static String validateName(String v) {
        String s = v == null ? "" : v.trim();
        if (s.length() < 2) return "Informe seu nome (m\u00ednimo 2 letras).";
        if (s.length() > 80) return "O nome pode ter at\u00e9 80 caracteres.";
        return null;
    }

    public static String validateUsername(String v) {
        String s = v == null ? "" : v.trim();
        if (!USERNAME.matcher(s).matches()) return "Use de 3 a 24 caracteres: letras, n\u00fameros, ponto ou sublinhado.";
        return null;
    }

    /** Vazio é aceito (o campo é opcional); a tela só chama isto quando há texto. */
    public static String validateBirthDate(String v, Calendar today) {
        int[] d = parseDate(v);
        if (d == null) return "Data inv\u00e1lida.";
        if (d[0] < 1900) return "Data fora do intervalo poss\u00edvel.";
        Calendar c = Calendar.getInstance();
        c.setLenient(false);
        try {
            c.clear();
            c.set(d[0], d[1] - 1, d[2]);
            c.getTimeInMillis(); // lança se for 31/02 etc.
        } catch (IllegalArgumentException e) {
            return "Data inv\u00e1lida.";
        }
        if (c.after(today)) return "A data n\u00e3o pode estar no futuro.";
        return null;
    }

    public static String validateWeight(String v) {
        Double n = parseNumber(v);
        if (v != null && !v.trim().isEmpty() && n == null) return "Digite um n\u00famero.";
        if (n != null && (n < 20 || n > 400)) return "Informe um peso entre 20 e 400 kg.";
        return null;
    }

    public static String validateHeight(String v) {
        Double n = parseNumber(v);
        if (v != null && !v.trim().isEmpty() && n == null) return "Digite um n\u00famero.";
        if (n != null && (n < 80 || n > 260)) return "Informe uma altura entre 80 e 260 cm.";
        return null;
    }

    /** Aceita "70,5" e "70.5". Vazio ou inv\u00e1lido -> null. */
    public static Double parseNumber(String v) {
        if (v == null) return null;
        String s = v.trim().replace(',', '.');
        if (s.isEmpty()) return null;
        try {
            double d = Double.parseDouble(s);
            return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "1990-05-17" -> {1990, 5, 17}; formato errado -> null. */
    public static int[] parseDate(String v) {
        if (v == null) return null;
        String[] p = v.trim().split("-");
        if (p.length != 3 || p[0].length() != 4 || p[1].length() != 2 || p[2].length() != 2) return null;
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatDate(int year, int month, int day) {
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month, day);
    }
}
