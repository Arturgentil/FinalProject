package com.stepvolt.app.safety;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;

import androidx.core.content.ContextCompat;

import com.stepvolt.app.core.Prefs;
import com.stepvolt.app.data.EmergencyContact;
import com.stepvolt.app.data.Repo;
import com.stepvolt.app.event.EventCategory;
import com.stepvolt.app.event.EventEngine;
import com.stepvolt.app.event.EventSeverity;
import com.stepvolt.app.gps.GeoMath;
import com.stepvolt.app.util.Notifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Contagem regressiva e envio do SOS.
 *
 * O app nunca dispara o SOS sozinho de imediato: abre uma contagem (30 s por
 * padrão) para a pessoa cancelar — falso alarme custa meio minuto. Ao zerar,
 * manda a mensagem com a localização aos contatos marcados.
 *
 * Envio: com a permissão de SMS concedida, o app manda direto, sem abrir
 * nada. Sem ela, abre o app de mensagens já preenchido (a pessoa só toca em
 * enviar). O app não faz ligações sozinho.
 */
public class SosManager {

    public interface Listener {
        void onTick(int secondsLeft, String reason);
        void onCancelled();
        void onSent(SendResult result);
    }

    public static final class SendResult {
        public final int sentDirect;
        public final int openedComposer;
        public final int contacts;
        public final String message;
        SendResult(int sentDirect, int openedComposer, int contacts, String message) {
            this.sentDirect = sentDirect; this.openedComposer = openedComposer;
            this.contacts = contacts; this.message = message;
        }
    }

    private static SosManager instance;

    public static synchronized SosManager get(Context ctx) {
        if (instance == null) instance = new SosManager(ctx.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private CountDownTimer timer;
    private String reason = "";
    private boolean counting = false;
    private GeoMath.Fix lastKnownFromApp;

    private SosManager(Context ctx) { this.ctx = ctx; }

    public void addListener(Listener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    public boolean isCounting() { return counting; }
    public String reason() { return reason; }

    /** O Telemetry atualiza aqui a última posição que o GPS da sessão obteve. */
    public void setLastFix(GeoMath.Fix fix) { this.lastKnownFromApp = fix; }

    /** Abre a contagem. Ignora se já houver uma em andamento. */
    public void startCountdown(final String why) {
        if (counting) return;
        counting = true;
        reason = why == null ? "" : why;
        final int total = Math.max(5, Prefs.sosCountdown(ctx));
        EventEngine.get(ctx).raise(EventCategory.EMERGENCY, EventSeverity.CRITICAL, "sos_countdown",
                "Contagem do SOS iniciada: " + reason);
        Notifier.showSosCountdown(ctx, total, reason);
        timer = new CountDownTimer(total * 1000L, 1000) {
            @Override public void onTick(long ms) {
                int left = (int) Math.ceil(ms / 1000.0);
                for (Listener l : listeners) l.onTick(left, reason);
            }
            @Override public void onFinish() {
                counting = false;
                send(reason);
            }
        }.start();
        for (Listener l : listeners) l.onTick(total, reason);
    }

    public void cancel() {
        if (!counting) return;
        if (timer != null) timer.cancel();
        counting = false;
        Notifier.cancelSosCountdown(ctx);
        EventEngine.get(ctx).raise(EventCategory.EMERGENCY, EventSeverity.LOW, "sos_cancelled",
                "SOS cancelado pela pessoa.");
        for (Listener l : listeners) l.onCancelled();
    }

    /** Envia agora, sem esperar a contagem. */
    public void sendNow(String why) {
        if (counting && timer != null) timer.cancel();
        counting = false;
        send(why == null || why.isEmpty() ? "Pedido manual de ajuda" : why);
    }

    private void send(final String why) {
        Notifier.cancelSosCountdown(ctx);
        Repo.get(ctx).contacts.allAsync(contacts -> {
            List<EmergencyContact> targets = new ArrayList<>();
            if (contacts != null) for (EmergencyContact c : contacts) if (c.notifySos) targets.add(c);

            final String message = buildMessage(why);
            int direct = 0, composer = 0;
            if (targets.isEmpty()) {
                Notifier.showSosResult(ctx, "SOS sem destino",
                        "Nenhum contato de emergência foi cadastrado. Cadastre em Segurança e SOS.");
            } else if (canSendSmsDirect()) {
                for (EmergencyContact c : targets) {
                    if (sendSms(c.phone, message)) direct++;
                }
                if (direct < targets.size()) composer = openComposer(targets, message);
            } else {
                composer = openComposer(targets, message);
            }

            EventEngine.get(ctx).raise(EventCategory.EMERGENCY, EventSeverity.CRITICAL, "sos_sent",
                    "SOS enviado: " + direct + " direto(s), " + composer + " pelo app de mensagens.");
            final SendResult result = new SendResult(direct, composer, targets.size(), message);
            main.post(() -> { for (Listener l : listeners) l.onSent(result); });
        });
    }

    public boolean canSendSmsDirect() {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean sendSms(String phone, String message) {
        try {
            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(message);
            sms.sendMultipartTextMessage(phone, null, parts, null, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int openComposer(List<EmergencyContact> targets, String message) {
        StringBuilder numbers = new StringBuilder();
        for (EmergencyContact c : targets) {
            if (numbers.length() > 0) numbers.append(';');
            numbers.append(c.phone);
        }
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers));
        i.putExtra("sms_body", message);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            return targets.size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Última posição conhecida, do GPS da sessão ou do que o sistema tiver guardado. */
    public Location lastLocation() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return null;
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best;
        } catch (SecurityException e) {
            return null;
        }
    }

    public String buildMessage(String why) {
        StringBuilder sb = new StringBuilder("SOS do StepVolt: preciso de ajuda.");
        if (why != null && !why.isEmpty()) sb.append(" Motivo: ").append(why).append('.');
        if (Prefs.sosSendLocation(ctx)) {
            double lat = Double.NaN, lon = Double.NaN;
            long ageMs = -1;
            if (lastKnownFromApp != null && System.currentTimeMillis() - lastKnownFromApp.ts < 5 * 60_000L) {
                lat = lastKnownFromApp.lat; lon = lastKnownFromApp.lon;
                ageMs = System.currentTimeMillis() - lastKnownFromApp.ts;
            } else {
                Location l = lastLocation();
                if (l != null) {
                    lat = l.getLatitude(); lon = l.getLongitude();
                    ageMs = System.currentTimeMillis() - l.getTime();
                }
            }
            if (!Double.isNaN(lat)) {
                sb.append(String.format(Locale.US, " Última localização: https://maps.google.com/?q=%.6f,%.6f", lat, lon));
                if (ageMs > 60_000) sb.append(String.format(Locale.US, " (há %d min)", ageMs / 60_000));
            } else {
                sb.append(" Localização indisponível.");
            }
        }
        return sb.toString();
    }
}
