package com.stepvolt.app.util;

import java.util.regex.Pattern;

/** Regras de e-mail e senha. As mesmas do servidor, para o erro aparecer antes de enviar. Sem Android. */
public final class AuthRules {

    private AuthRules() {}

    // Mesmo formato aceito pelo servidor (utils/validate.js).
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,255}\\.[^\\s@]{2,}$");
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 128;

    public static String validateEmail(String v) {
        String s = v == null ? "" : v.trim();
        if (s.isEmpty()) return "Digite seu e-mail.";
        if (s.length() > 320 || !EMAIL.matcher(s).matches()) return "Esse e-mail n\u00e3o parece certo. Exemplo: nome@email.com";
        return null;
    }

    public static String validatePassword(String v) {
        if (v == null || v.isEmpty()) return "Crie uma senha.";
        if (v.length() < PASSWORD_MIN) return "A senha precisa de pelo menos " + PASSWORD_MIN + " caracteres.";
        if (v.length() > PASSWORD_MAX) return "A senha pode ter at\u00e9 " + PASSWORD_MAX + " caracteres.";
        return null;
    }

    public static String validateConfirm(String password, String confirm) {
        if (confirm == null || confirm.isEmpty()) return "Repita a senha.";
        if (!confirm.equals(password)) return "As senhas n\u00e3o s\u00e3o iguais.";
        return null;
    }

    /** 0 = vazia/curta demais ... 4 = forte. S\u00f3 informativo: o m\u00ednimo de verdade \u00e9 PASSWORD_MIN. */
    public static int strength(String p) {
        if (p == null || p.isEmpty()) return 0;
        if (p.length() < PASSWORD_MIN) return 1;
        String lower = p.toLowerCase();
        if (isCommon(lower) || allSame(p)) return 1;
        int score = 1;
        if (p.length() >= 12) score++;
        boolean letters = false, digits = false, upper = false, lowerC = false, symbol = false;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (Character.isDigit(c)) digits = true;
            else if (Character.isLetter(c)) { letters = true; if (Character.isUpperCase(c)) upper = true; else lowerC = true; }
            else symbol = true;
        }
        if (letters && digits) score++;
        if ((upper && lowerC) || symbol) score++;
        return Math.min(4, score);
    }

    public static String strengthLabel(int s) {
        switch (s) {
            case 0: return "Use 8 ou mais caracteres, com letras e n\u00fameros.";
            case 1: return "Fraca: tente um pouco mais longa ou menos previs\u00edvel.";
            case 2: return "Razo\u00e1vel. Misturar letras e n\u00fameros ajuda.";
            case 3: return "Boa senha.";
            default: return "Senha forte.";
        }
    }

    private static boolean allSame(String p) {
        for (int i = 1; i < p.length(); i++) if (p.charAt(i) != p.charAt(0)) return false;
        return true;
    }

    private static boolean isCommon(String lower) {
        String[] common = {"12345678", "123456789", "1234567890", "password", "senha123", "qwertyui", "qwerty123",
                "abcd1234", "11111111", "00000000", "senhasenha", "password1", "iloveyou"};
        for (String c : common) if (lower.equals(c)) return true;
        return false;
    }
}
