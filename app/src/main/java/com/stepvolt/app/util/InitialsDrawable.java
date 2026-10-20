package com.stepvolt.app.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Avatar sem foto: fundo colorido com as iniciais. Sem nome, desenha uma
 * silhueta de pessoa. O recorte (redondo ou arredondado) quem faz é a própria
 * ImageView, então aqui basta preencher o quadrado.
 */
public class InitialsDrawable extends Drawable {

    private static final int[] PALETTE = {
            0xFF00F5A0, 0xFF4CC9F0, 0xFF8B5CFF, 0xFFFFB347, 0xFFFF5C7A, 0xFF5B8CFF};
    private static final int INK = 0xFF07070F;

    private final String initials;
    private final int color;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public InitialsDrawable(String name) {
        this.initials = AvatarText.initials(name);
        this.color = PALETTE[AvatarText.colorIndex(name, PALETTE.length)];
        fill.setColor(initials.isEmpty() ? 0xFF2A2A44 : color);
        text.setColor(INK);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, fill);
        float w = b.width(), h = b.height();
        if (!initials.isEmpty()) {
            text.setTextSize(Math.min(w, h) * (initials.length() > 1 ? 0.38f : 0.46f));
            float y = b.centerY() - (text.descent() + text.ascent()) / 2f;
            c.drawText(initials, b.centerX(), y, text);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF6A6A8E);
            float cx = b.centerX();
            c.drawCircle(cx, b.top + h * 0.38f, w * 0.17f, p);
            c.drawCircle(cx, b.top + h * 1.02f, w * 0.34f, p);
        }
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); text.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); text.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
