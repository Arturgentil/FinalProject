package com.stepvolt.app.util;

/** Contas de recorte e redução da foto de perfil. Sem Android, para poder testar. */
public final class ImageMath {

    private ImageMath() {}

    public static final int TARGET = 512;

    /** Maior potência de 2 que ainda deixa o lado MENOR com pelo menos `target` pixels. */
    public static int sampleSize(int width, int height, int target) {
        int shortSide = Math.min(width, height);
        int s = 1;
        while (shortSide / (s * 2) >= target) s *= 2;
        return s;
    }

    /** Quadrado central: {left, top, lado}. */
    public static int[] centerSquare(int width, int height) {
        int side = Math.min(width, height);
        return new int[]{(width - side) / 2, (height - side) / 2, side};
    }

    /** Orientação EXIF (1..8) -> graus para girar e se precisa espelhar. */
    public static int[] exifToRotation(int orientation) {
        switch (orientation) {
            case 3: return new int[]{180, 0};
            case 6: return new int[]{90, 0};
            case 8: return new int[]{270, 0};
            case 2: return new int[]{0, 1};
            case 4: return new int[]{180, 1};
            case 5: return new int[]{90, 1};
            case 7: return new int[]{270, 1};
            default: return new int[]{0, 0};
        }
    }

    /** Qualidade JPEG para a próxima tentativa, descendo até um piso aceitável. */
    public static int nextQuality(int current) {
        return current > 55 ? current - 10 : -1;
    }
}
