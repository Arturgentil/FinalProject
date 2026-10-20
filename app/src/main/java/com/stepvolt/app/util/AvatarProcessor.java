package com.stepvolt.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Prepara a foto de perfil: corrige a rotação (EXIF), recorta o quadrado
 * central, reduz para 512 px e comprime em JPEG. A foto original de um celular
 * tem alguns MB; o que sobe tem algumas dezenas de KB.
 */
public final class AvatarProcessor {

    private AvatarProcessor() {}

    /** O servidor recusa mais de 400 KB; ficamos com folga. */
    public static final int MAX_BYTES = 300 * 1024;
    private static final int MIN_SIDE = 64;

    public static byte[] process(Context ctx, Uri uri) throws IOException {
        int width, height, orientation = ExifInterface.ORIENTATION_NORMAL;

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Não consegui abrir essa imagem.");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        width = bounds.outWidth;
        height = bounds.outHeight;
        if (width <= 0 || height <= 0) throw new IOException("Esse arquivo não parece ser uma imagem.");
        if (Math.min(width, height) < MIN_SIDE) throw new IOException("Imagem pequena demais. Escolha uma foto maior.");

        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (IOException | RuntimeException ignored) {
            // sem EXIF (PNG, captura de tela): segue sem girar
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = ImageMath.sampleSize(width, height, ImageMath.TARGET);
        Bitmap decoded;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Não consegui abrir essa imagem.");
            decoded = BitmapFactory.decodeStream(in, null, opts);
        } catch (OutOfMemoryError e) {
            throw new IOException("A imagem é grande demais para este aparelho.");
        }
        if (decoded == null) throw new IOException("Não consegui ler essa imagem.");

        Bitmap square = null;
        try {
            int[] box = ImageMath.centerSquare(decoded.getWidth(), decoded.getHeight());
            int[] rot = ImageMath.exifToRotation(orientation);
            Matrix m = new Matrix();
            if (rot[1] == 1) m.postScale(-1f, 1f);
            if (rot[0] != 0) m.postRotate(rot[0]);
            float scale = box[2] > ImageMath.TARGET ? ImageMath.TARGET / (float) box[2] : 1f;
            if (scale != 1f) m.postScale(scale, scale);
            square = Bitmap.createBitmap(decoded, box[0], box[1], box[2], box[2], m, true);

            int quality = 88;
            while (true) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                square.compress(Bitmap.CompressFormat.JPEG, quality, out);
                byte[] bytes = out.toByteArray();
                if (bytes.length <= MAX_BYTES) return bytes;
                quality = ImageMath.nextQuality(quality);
                if (quality < 0) throw new IOException("Não consegui reduzir essa foto o bastante. Tente outra.");
            }
        } catch (OutOfMemoryError e) {
            throw new IOException("A imagem é grande demais para este aparelho.");
        } finally {
            if (square != null && square != decoded) square.recycle();
            decoded.recycle();
        }
    }
}
