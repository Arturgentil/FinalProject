package com.stepvolt.app.util;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/**
 * Animações curtas usadas pelo app.
 *
 * Regra que guiou a escolha: movimento responde a alguma coisa que o usuário
 * fez ou a um dado que mudou de verdade. Não há entrada animada em cada cartão
 * de cada tela — só a sequência de quando o tênis conecta e os números mudando.
 */
public final class Anim {

    private Anim() {}

    /** Mostra a view com fade curto, sem piscar se ela já estiver visível. */
    public static void fadeIn(View v, long duration) {
        if (v == null) return;
        if (v.getVisibility() == View.VISIBLE && v.getAlpha() == 1f) return;
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(duration)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    public static void fadeOut(final View v, long duration) {
        if (v == null || v.getVisibility() != View.VISIBLE) return;
        v.animate().alpha(0f).setDuration(duration)
                .withEndAction(() -> v.setVisibility(View.GONE)).start();
    }

    /** Entrada em sequência, usada uma vez só: quando o tênis acaba de conectar. */
    public static void revealStaggered(View[] views, long stepMs) {
        if (views == null) return;
        for (int i = 0; i < views.length; i++) {
            final View v = views[i];
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(16f);
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(i * stepMs)
                    .setDuration(320)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    /** Número que sobe até o valor novo, em vez de trocar de uma vez. */
    public static void countTo(final TextView target, double from, final double to,
                               final int decimals, final String suffix) {
        if (target == null) return;
        if (Math.abs(to - from) < 0.001) {
            target.setText(Fmt.decimals(to, decimals) + suffix);
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat((float) from, (float) to);
        a.setDuration(420);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(animation -> {
            float v = (Float) animation.getAnimatedValue();
            target.setText(Fmt.decimals(v, decimals) + suffix);
        });
        a.start();
    }

    /** Pulso leve: confirma que um dado novo chegou sem chamar atenção demais. */
    public static void tick(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(110)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start())
                .start();
    }

    /** Resposta ao toque em cartões que abrem outra tela. */
    public static void press(View v, final Runnable then) {
        if (v == null) {
            if (then != null) then.run();
            return;
        }
        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                        .withEndAction(then).start())
                .start();
    }
}
