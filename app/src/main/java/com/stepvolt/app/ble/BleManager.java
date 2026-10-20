package com.stepvolt.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.stepvolt.app.core.Prefs;

import com.stepvolt.app.protocol.PacketCodec;
import com.stepvolt.app.protocol.StreamAssembler;

import java.util.HashMap;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Camada Bluetooth do StepVolt.
 *
 * PROTOCOLO (o firmware do ESP32 e esta classe precisam combinar):
 *
 *   Service:  6E400001-B5A3-F393-E0A9-E50E24DCCA9E   (Nordic UART Service)
 *   TX:       6E400003-B5A3-F393-E0A9-E50E24DCCA9E   (ESP32 -> celular, notify)
 *   RX:       6E400002-B5A3-F393-E0A9-E50E24DCCA9E   (celular -> ESP32, write)
 *
 * Cada notificação é um objeto JSON em UTF-8, por exemplo:
 *
 *   {"bpm":82,"spo2":97,"steps":3,"energy":1.8,"temp":33.2,"battery":74}
 *
 *   - "steps" e "energy" são o acréscimo desde o último pacote (delta)
 *   - as demais chaves são leituras instantâneas
 *   - chaves numéricas desconhecidas são guardadas mesmo assim, então dá para
 *     acrescentar sensores no firmware antes de mexer no app
 *   - "fw" (texto) é lido como versão do firmware, se vier
 *
 * O que esta versão resolve em relação à anterior:
 *   - máquina de estados de verdade (a antiga só tinha um boolean "connected")
 *   - lista de dispositivos encontrados, para o usuário escolher qual é o tênis
 *   - lembra o último tênis e reconecta sozinho, com espera crescente
 *   - remonta pacotes JSON que chegaram partidos em várias notificações
 *   - pede MTU maior, lê RSSI e registra tudo num log visível na aba do tênis
 *   - callbacks de notificação nas duas formas (antes e depois da API 33)
 */
public class BleManager {

    private static final String TAG = "BleManager";

    public static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID CHAR_TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID CHAR_RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final long SCAN_TIMEOUT_MS = 15000;
    private static final long CONNECT_TIMEOUT_MS = 12000;
    private static final long RSSI_INTERVAL_MS = 4000;
    private static final long STALE_DATA_MS = 12000;
    private static final int MAX_RECONNECT_ATTEMPTS = 6;
    private static final int LOG_CAPACITY = 250;
    private static final int PREFERRED_MTU = 185;

    /** Um aparelho visto durante a varredura. */
    public static class DiscoveredDevice {
        public final String address;
        public String name;
        public int rssi;
        public boolean advertisesService;
        public long lastSeen;

        DiscoveredDevice(String address, String name, int rssi, boolean advertisesService) {
            this.address = address;
            this.name = name;
            this.rssi = rssi;
            this.advertisesService = advertisesService;
            this.lastSeen = System.currentTimeMillis();
        }

        public String displayName() {
            return (name == null || name.trim().isEmpty()) ? address : name;
        }

        /** Heurística: parece ser o tênis StepVolt? */
        public boolean looksLikeShoe() {
            if (advertisesService) return true;
            if (name == null) return false;
            String n = name.toLowerCase();
            return n.contains("stepvolt") || n.contains("esp32") || n.contains("shoe") || n.contains("tenis");
        }
    }

    public interface Listener {
        default void onStateChanged(BleState state, String detail) {}
        default void onSample(SensorSample sample) {}
        default void onDevicesUpdated(List<DiscoveredDevice> devices) {}
        default void onLog(BleEvent event) {}
        default void onError(String message) {}
    }

    private final Context appContext;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<DiscoveredDevice> discovered = new ArrayList<>();
    private final List<BleEvent> log = new ArrayList<>();
    private final StreamAssembler assembler = new StreamAssembler();

    // Protocolo v2: contadores de qualidade do enlace e comandos com confirmação.
    private int protocolVersion = 1;
    private long invalidPackets = 0;
    private long lostPackets = 0;
    private long lastSeq = -1;
    private int nextCommandId = 1;
    private final Map<Integer, PendingCommand> pendingCommands = new HashMap<>();

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;

    private BleState state = BleState.IDLE;
    private String stateDetail = "";
    private String deviceName;
    private String deviceAddress;
    private String firmwareVersion;

    private boolean scanning;
    private boolean autoConnectOnMatch = true;
    private boolean userWantsConnection;
    private int reconnectAttempt;

    private long connectedSince;
    private long lastPacketAt;
    private long packetCount;
    private int rssi = 0;
    private int mtu = 23;
    private double batteryPct = -1;

    public BleManager(Context context) {
        this.appContext = context.getApplicationContext();
        BluetoothManager bm = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) adapter = bm.getAdapter();
    }

    // ------------------------------------------------------------------
    // Listeners e estado
    // ------------------------------------------------------------------

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public BleState getState() { return state; }
    public String getStateDetail() { return stateDetail; }
    public boolean isConnected() { return state == BleState.CONNECTED; }
    public String getConnectedDeviceName() { return deviceName; }
    public String getConnectedDeviceAddress() { return deviceAddress; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public long getConnectedSince() { return connectedSince; }
    public long getPacketCount() { return packetCount; }
    public long getLastPacketAt() { return lastPacketAt; }
    public int getRssi() { return rssi; }
    public int getMtu() { return mtu; }
    public double getBatteryPct() { return batteryPct; }
    public boolean isScanning() { return scanning; }
    public int getProtocolVersion() { return protocolVersion; }
    public long getInvalidPackets() { return invalidPackets; }
    public long getLostPackets() { return lostPackets; }

    /** Percentual de pacotes perdidos ou descartados desde que conectou. */
    public double getPacketLossPct() {
        long total = packetCount + lostPackets + invalidPackets;
        if (total <= 0) return 0;
        return (lostPackets + invalidPackets) * 100.0 / total;
    }

    public boolean isDataStale() {
        return state == BleState.CONNECTED && lastPacketAt > 0
                && System.currentTimeMillis() - lastPacketAt > STALE_DATA_MS;
    }

    public List<DiscoveredDevice> getDiscovered() {
        synchronized (discovered) { return new ArrayList<>(discovered); }
    }

    public List<BleEvent> getLog() {
        synchronized (log) { return new ArrayList<>(log); }
    }

    public void clearLog() {
        synchronized (log) { log.clear(); }
    }

    private void setState(BleState next, String detail) {
        this.state = next;
        this.stateDetail = detail == null ? "" : detail;
        final BleState s = next;
        final String d = this.stateDetail;
        main.post(() -> {
            for (Listener l : listeners) l.onStateChanged(s, d);
        });
    }

    private void addLog(int level, String message) {
        final BleEvent ev = new BleEvent(level, message);
        synchronized (log) {
            log.add(ev);
            while (log.size() > LOG_CAPACITY) log.remove(0);
        }
        Log.d(TAG, message);
        main.post(() -> {
            for (Listener l : listeners) l.onLog(ev);
        });
    }

    private void emitError(final String message) {
        addLog(BleEvent.ERROR, message);
        main.post(() -> {
            for (Listener l : listeners) l.onError(message);
        });
    }

    // ------------------------------------------------------------------
    // Permissões e pré-requisitos
    // ------------------------------------------------------------------

    private boolean granted(String perm) {
        return ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return granted(android.Manifest.permission.BLUETOOTH_SCAN)
                    && granted(android.Manifest.permission.BLUETOOTH_CONNECT);
        }
        return granted(android.Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public static String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public boolean isBluetoothOn() {
        return adapter != null && adapter.isEnabled();
    }

    /** Verifica o que falta antes de tentar conectar. Retorna true se dá para seguir. */
    private boolean preflight() {
        if (adapter == null) {
            setState(BleState.FAILED, "Este aparelho não tem Bluetooth.");
            return false;
        }
        if (!adapter.isEnabled()) {
            setState(BleState.BLUETOOTH_OFF, "Ative o Bluetooth para conectar o tênis.");
            return false;
        }
        if (!hasPermissions()) {
            setState(BleState.NO_PERMISSION, "Falta a permissão de Bluetooth.");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Varredura
    // ------------------------------------------------------------------

    /**
     * Ponto de entrada principal: se já existe um tênis lembrado, conecta nele
     * direto; senão faz a varredura e conecta no primeiro que parecer o tênis.
     */
    public void connect() {
        userWantsConnection = true;
        reconnectAttempt = 0;
        String remembered = Prefs.getString(appContext, Prefs.LAST_DEVICE_ADDR, null);
        if (remembered != null && BluetoothAdapter.checkBluetoothAddress(remembered)) {
            connectToAddress(remembered);
        } else {
            startScan(true);
        }
    }

    /** Varredura só para listar o que está por perto (aba do tênis). */
    public void scanOnly() {
        startScan(false);
    }

    @SuppressLint("MissingPermission")
    private void startScan(boolean autoConnectFirstMatch) {
        if (!preflight()) return;
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            emitError("Este aparelho não suporta Bluetooth LE.");
            setState(BleState.FAILED, "Bluetooth LE indisponível.");
            return;
        }
        if (scanning) stopScan();

        synchronized (discovered) { discovered.clear(); }
        publishDevices();

        autoConnectOnMatch = autoConnectFirstMatch;
        scanning = true;
        setState(BleState.SCANNING, "Procurando o tênis…");
        addLog(BleEvent.INFO, "Varredura iniciada");

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        // Sem filtro de serviço de propósito: muitos firmwares de ESP32 não
        // anunciam o UUID no pacote de advertising e com filtro nunca
        // apareceriam. O casamento é feito no callback.
        try {
            scanner.startScan(null, settings, scanCallback);
        } catch (Exception e) {
            scanning = false;
            emitError("Não foi possível iniciar a varredura: " + e.getMessage());
            setState(BleState.FAILED, "Falha na varredura.");
            return;
        }

        main.postDelayed(scanTimeout, SCAN_TIMEOUT_MS);
    }

    private final Runnable scanTimeout = new Runnable() {
        @Override
        public void run() {
            if (!scanning) return;
            stopScan();
            if (state == BleState.SCANNING) {
                addLog(BleEvent.WARN, "Nenhum tênis encontrado na varredura");
                setState(BleState.FAILED, "Nenhum tênis encontrado. Confira se ele está ligado e por perto.");
            }
        }
    };

    @SuppressLint("MissingPermission")
    public void stopScan() {
        main.removeCallbacks(scanTimeout);
        if (scanner != null && scanning) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        }
        scanning = false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device == null) return;

            boolean advertisesService = false;
            if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null) {
                for (android.os.ParcelUuid p : result.getScanRecord().getServiceUuids()) {
                    if (SERVICE_UUID.equals(p.getUuid())) { advertisesService = true; break; }
                }
            }

            String name = null;
            try {
                name = device.getName();
                if (name == null && result.getScanRecord() != null) {
                    name = result.getScanRecord().getDeviceName();
                }
            } catch (SecurityException ignored) {}

            DiscoveredDevice found = upsert(device.getAddress(), name, result.getRssi(), advertisesService);
            publishDevices();

            if (autoConnectOnMatch && found.looksLikeShoe() && state == BleState.SCANNING) {
                addLog(BleEvent.INFO, "Tênis identificado: " + found.displayName());
                stopScan();
                connectToAddress(found.address);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            emitError("A varredura falhou (código " + errorCode + ").");
            setState(BleState.FAILED, "A varredura falhou.");
        }
    };

    private DiscoveredDevice upsert(String address, String name, int rssiValue, boolean hasService) {
        synchronized (discovered) {
            for (DiscoveredDevice d : discovered) {
                if (d.address.equals(address)) {
                    if (name != null) d.name = name;
                    d.rssi = rssiValue;
                    d.advertisesService = d.advertisesService || hasService;
                    d.lastSeen = System.currentTimeMillis();
                    return d;
                }
            }
            DiscoveredDevice d = new DiscoveredDevice(address, name, rssiValue, hasService);
            discovered.add(d);
            return d;
        }
    }

    private void publishDevices() {
        final List<DiscoveredDevice> copy = getDiscovered();
        main.post(() -> {
            for (Listener l : listeners) l.onDevicesUpdated(copy);
        });
    }

    // ------------------------------------------------------------------
    // Conexão
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    public void connectToAddress(String address) {
        if (!preflight()) return;
        userWantsConnection = true;
        stopScan();
        closeGatt();

        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(address);
        } catch (IllegalArgumentException e) {
            emitError("Endereço de dispositivo inválido.");
            return;
        }

        deviceAddress = address;
        try {
            String n = device.getName();
            if (n != null) deviceName = n;
        } catch (SecurityException ignored) {}
        if (deviceName == null) deviceName = Prefs.getString(appContext, Prefs.LAST_DEVICE_NAME, address);

        setState(reconnectAttempt > 0 ? BleState.RECONNECTING : BleState.CONNECTING,
                "Conectando em " + deviceName + "…");
        addLog(BleEvent.INFO, "Conectando em " + deviceName + " (" + address + ")");

        assembler.reset();
        failAllPendingCommands("Conexão perdida antes da resposta do tênis.");
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS);
    }

    private final Runnable connectTimeout = new Runnable() {
        @Override
        public void run() {
            if (state == BleState.CONNECTING || state == BleState.DISCOVERING || state == BleState.RECONNECTING) {
                addLog(BleEvent.WARN, "Tempo esgotado esperando o tênis responder");
                closeGatt();
                scheduleReconnect("O tênis não respondeu a tempo.");
            }
        }
    };

    /** Desconexão pedida pelo usuário: não tenta reconectar. */
    public void disconnect() {
        userWantsConnection = false;
        reconnectAttempt = 0;
        main.removeCallbacks(reconnectRunnable);
        main.removeCallbacks(connectTimeout);
        main.removeCallbacks(rssiRunnable);
        stopScan();
        addLog(BleEvent.INFO, "Desconectado pelo usuário");
        closeGatt();
        connectedSince = 0;
        setState(BleState.IDLE, "");
    }

    @SuppressLint("MissingPermission")
    private void closeGatt() {
        if (gatt != null) {
            try { gatt.disconnect(); } catch (Exception ignored) {}
            try { gatt.close(); } catch (Exception ignored) {}
            gatt = null;
        }
    }

    /** Esquece o tênis lembrado (botão "Esquecer este tênis"). */
    public void forgetDevice() {
        Prefs.remove(appContext, Prefs.LAST_DEVICE_ADDR);
        Prefs.remove(appContext, Prefs.LAST_DEVICE_NAME);
        addLog(BleEvent.INFO, "Tênis removido dos lembrados");
    }

    // ------------------------------------------------------------------
    // Reconexão com espera crescente
    // ------------------------------------------------------------------

    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!userWantsConnection) return;
            if (deviceAddress != null) {
                connectToAddress(deviceAddress);
            } else {
                startScan(true);
            }
        }
    };

    private void scheduleReconnect(String reason) {
        main.removeCallbacks(connectTimeout);
        if (!userWantsConnection) {
            setState(BleState.IDLE, "");
            return;
        }
        if (!Prefs.getBool(appContext, Prefs.AUTO_CONNECT, true)) {
            // Ajustes > "Reconectar sozinho" desligado: não insiste; a pessoa decide quando tentar.
            userWantsConnection = false;
            setState(BleState.FAILED, reason + " Toque para reconectar.");
            return;
        }
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            userWantsConnection = false;
            setState(BleState.FAILED, reason + " Toque para tentar de novo.");
            emitError("Não consegui reconectar no tênis depois de várias tentativas.");
            return;
        }
        reconnectAttempt++;
        long delay = Math.min(30000L, (long) (1500 * Math.pow(2, reconnectAttempt - 1)));
        setState(BleState.RECONNECTING,
                "Reconectando em " + (delay / 1000) + "s (tentativa " + reconnectAttempt + ")");
        addLog(BleEvent.WARN, reason + " Nova tentativa em " + (delay / 1000) + "s.");
        main.postDelayed(reconnectRunnable, delay);
    }

    // ------------------------------------------------------------------
    // GATT
    // ------------------------------------------------------------------

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(final BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                main.removeCallbacks(connectTimeout);
                addLog(BleEvent.INFO, "Conectado no GATT, procurando serviços");
                setState(BleState.DISCOVERING, "Preparando os sensores…");
                try { g.requestMtu(PREFERRED_MTU); } catch (Exception ignored) {}
                main.postDelayed(() -> {
                    try { g.discoverServices(); } catch (Exception ignored) {}
                }, 400);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                boolean wasConnected = state == BleState.CONNECTED;
                connectedSince = 0;
                main.removeCallbacks(rssiRunnable);
                try { g.close(); } catch (Exception ignored) {}
                if (gatt == g) gatt = null;
                if (wasConnected) {
                    addLog(BleEvent.WARN, "Ligação perdida (status " + status + ")");
                    scheduleReconnect("A ligação com o tênis caiu.");
                } else {
                    scheduleReconnect("Não consegui completar a conexão (status " + status + ").");
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int newMtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mtu = newMtu;
                addLog(BleEvent.INFO, "MTU ajustado para " + newMtu + " bytes");
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emitError("Não consegui ler os serviços do tênis.");
                closeGatt();
                scheduleReconnect("Falha ao ler os serviços.");
                return;
            }
            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service == null) {
                emitError("Este aparelho não expõe o serviço esperado pelo StepVolt. "
                        + "Confira se o firmware do ESP32 é o do projeto.");
                closeGatt();
                userWantsConnection = false;
                setState(BleState.FAILED, "Serviço do tênis não encontrado.");
                return;
            }
            BluetoothGattCharacteristic tx = service.getCharacteristic(CHAR_TX_UUID);
            if (tx == null) {
                emitError("O firmware não tem a característica de dados (TX).");
                closeGatt();
                userWantsConnection = false;
                setState(BleState.FAILED, "Característica de dados ausente.");
                return;
            }

            g.setCharacteristicNotification(tx, true);
            BluetoothGattDescriptor cccd = tx.getDescriptor(CCCD_UUID);
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(cccd);
                }
            } else {
                addLog(BleEvent.WARN, "Sem descritor CCCD; seguindo mesmo assim");
                markConnected();
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
            if (CCCD_UUID.equals(descriptor.getUuid())) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    markConnected();
                } else {
                    emitError("Não consegui ligar as notificações do tênis.");
                    closeGatt();
                    scheduleReconnect("Falha ao ligar as notificações.");
                }
            }
        }

        // Android 12 e anteriores
        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return;
            handleIncoming(ch.getValue());
        }

        // Android 13 em diante
        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch, byte[] value) {
            handleIncoming(value);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt g, int value, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) rssi = value;
        }
    };

    private void markConnected() {
        reconnectAttempt = 0;
        connectedSince = System.currentTimeMillis();
        packetCount = 0;
        lastPacketAt = 0;
        invalidPackets = 0;
        lostPackets = 0;
        lastSeq = -1;
        protocolVersion = 1;
        assembler.reset();
        if (deviceAddress != null) {
            Prefs.setString(appContext, Prefs.LAST_DEVICE_ADDR, deviceAddress);
            Prefs.setString(appContext, Prefs.LAST_DEVICE_NAME, deviceName == null ? deviceAddress : deviceName);
        }
        addLog(BleEvent.INFO, "Tênis pronto, recebendo dados");
        setState(BleState.CONNECTED, deviceName);
        main.postDelayed(rssiRunnable, RSSI_INTERVAL_MS);
    }

    private final Runnable rssiRunnable = new Runnable() {
        @Override
        @SuppressLint("MissingPermission")
        public void run() {
            if (state != BleState.CONNECTED || gatt == null) return;
            try { gatt.readRemoteRssi(); } catch (Exception ignored) {}
            main.postDelayed(this, RSSI_INTERVAL_MS);
        }
    };

    // ------------------------------------------------------------------
    // Leitura dos pacotes
    // ------------------------------------------------------------------

    /**
     * Junta os bytes recebidos, recorta pacotes completos e confere o checksum
     * (protocolo v2). Pacote com checksum errado é descartado e contado — nunca
     * vira dado nas telas nem no banco.
     */
    private void handleIncoming(byte[] value) {
        if (value == null || value.length == 0) return;
        String chunk = new String(value, Charset.forName("UTF-8"));
        List<String> packets = assembler.feed(chunk);
        for (String line : packets) {
            PacketCodec.Decoded d = PacketCodec.decode(line);
            if (!d.valid) {
                invalidPackets++;
                if (invalidPackets <= 5 || invalidPackets % 25 == 0) {
                    addLog(BleEvent.WARN, "Pacote descartado (" + d.error + "), total: " + invalidPackets);
                }
                continue;
            }
            dispatchSample(d.json);
        }
    }

    private void dispatchSample(String json) {
        final SensorSample sample = parseSample(json);
        if (sample == null || sample.isEmpty()) return;
        packetCount++;
        lastPacketAt = System.currentTimeMillis();
        Double batt = sample.getOrNull(SensorCatalog.BATTERY);
        if (batt != null) batteryPct = batt;
        main.post(() -> {
            for (Listener l : listeners) l.onSample(sample);
        });
    }

    /** Chaves de controle do protocolo: não são grandezas e nunca viram amostra. */
    private static boolean isProtocolKey(String key) {
        String k = key.toLowerCase();
        return k.equals("v") || k.equals("seq") || k.equals("ack") || k.equals("id")
                || k.equals("ok") || k.equals("crc") || k.equals("fw") || k.equals("firmware");
    }

    /**
     * Converte o JSON do firmware num SensorSample. Qualquer chave numérica é
     * aceita; as conhecidas ganham o nome canônico do catálogo, as demais ficam
     * guardadas com o nome cru para não se perderem. Respostas de comando
     * ("ack") não geram amostra: resolvem o comando pendente.
     */
    public SensorSample parseSample(String json) {
        try {
            JSONObject o = new JSONObject(json);

            if (o.has("v")) protocolVersion = o.optInt("v", protocolVersion);

            if (o.has("ack")) {
                handleAck(o);
                return null;
            }

            if (o.has("seq")) {
                long seq = o.optLong("seq", -1);
                if (lastSeq >= 0 && seq > lastSeq + 1) lostPackets += seq - lastSeq - 1;
                lastSeq = seq;
            }

            Map<String, Double> values = new LinkedHashMap<>();
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object v = o.opt(key);
                if ("fw".equalsIgnoreCase(key) || "firmware".equalsIgnoreCase(key)) {
                    firmwareVersion = String.valueOf(v);
                    continue;
                }
                if (isProtocolKey(key)) continue;
                if (v instanceof Number) {
                    values.put(SensorCatalog.canonical(key), ((Number) v).doubleValue());
                } else if (v instanceof Boolean) {
                    values.put(SensorCatalog.canonical(key), ((Boolean) v) ? 1.0 : 0.0);
                } else if (v instanceof String) {
                    try {
                        values.put(SensorCatalog.canonical(key), Double.parseDouble((String) v));
                    } catch (NumberFormatException ignored) {}
                }
            }
            return new SensorSample(System.currentTimeMillis(), values, json);
        } catch (Exception e) {
            addLog(BleEvent.WARN, "Pacote fora do formato esperado: " + json);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Canal de comandos (celular -> tênis)
    // ------------------------------------------------------------------

    /** Envia um comando de texto ao tênis (ex.: "CALIBRAR", "LED_ON"). */
    @SuppressLint("MissingPermission")
    public boolean sendCommand(String command) {
        if (gatt == null || state != BleState.CONNECTED) {
            emitError("Conecte o tênis antes de enviar um comando.");
            return false;
        }
        BluetoothGattService service = gatt.getService(SERVICE_UUID);
        if (service == null) return false;
        BluetoothGattCharacteristic rx = service.getCharacteristic(CHAR_RX_UUID);
        if (rx == null) {
            emitError("Este firmware não aceita comandos (sem característica RX).");
            return false;
        }
        byte[] payload = command.getBytes(Charset.forName("UTF-8"));
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(rx, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                rx.setValue(payload);
                rx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                gatt.writeCharacteristic(rx);
            }
            addLog(BleEvent.DATA, "Comando enviado: " + command);
            return true;
        } catch (Exception e) {
            emitError("Não consegui enviar o comando: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Comandos com confirmação (protocolo v2)
    // ------------------------------------------------------------------

    public interface CommandCallback {
        void onResult(boolean ok, String detail);
    }

    private static final class PendingCommand {
        final String name;
        final String arg;
        final CommandCallback callback;
        int attempts = 0;
        Runnable timeout;
        PendingCommand(String name, String arg, CommandCallback callback) {
            this.name = name; this.arg = arg; this.callback = callback;
        }
    }

    private static final long ACK_TIMEOUT_MS = 3000;
    private static final int ACK_MAX_ATTEMPTS = 2;

    /**
     * Envia um comando e espera a resposta do tênis. Sem resposta em 3 s,
     * tenta mais uma vez; se continuar sem resposta, avisa que falhou — em vez
     * de fingir que deu certo. Com firmware v1 (sem confirmação) o comando é
     * enviado no formato antigo e o resultado diz isso claramente.
     */
    public void sendCommandAcked(final String name, final String arg, final CommandCallback cb) {
        if (!isConnected()) {
            if (cb != null) cb.onResult(false, "Conecte o tênis antes de enviar um comando.");
            return;
        }
        if (protocolVersion < 2) {
            boolean sent = sendCommand(name);
            if (cb != null) cb.onResult(sent, sent
                    ? "Enviado. Este firmware não confirma comandos."
                    : "Não foi possível enviar.");
            return;
        }
        final int id;
        final PendingCommand pc = new PendingCommand(name, arg, cb);
        synchronized (pendingCommands) {
            id = nextCommandId++;
            pendingCommands.put(id, pc);
        }
        transmit(id, pc);
    }

    private void transmit(final int id, final PendingCommand pc) {
        pc.attempts++;
        boolean sent = sendCommand(PacketCodec.encodeCommand(pc.name, pc.arg, id));
        if (!sent) {
            finishCommand(id, false, "Não foi possível enviar.");
            return;
        }
        pc.timeout = new Runnable() {
            @Override
            public void run() {
                boolean stillPending;
                synchronized (pendingCommands) { stillPending = pendingCommands.containsKey(id); }
                if (!stillPending) return;
                if (pc.attempts < ACK_MAX_ATTEMPTS) {
                    addLog(BleEvent.WARN, "Sem resposta ao comando " + pc.name + ", tentando de novo");
                    transmit(id, pc);
                } else {
                    finishCommand(id, false, "O tênis não respondeu ao comando " + pc.name + ".");
                }
            }
        };
        main.postDelayed(pc.timeout, ACK_TIMEOUT_MS);
    }

    private void failAllPendingCommands(String reason) {
        List<Integer> ids;
        synchronized (pendingCommands) { ids = new ArrayList<>(pendingCommands.keySet()); }
        for (int id : ids) finishCommand(id, false, reason);
    }

    private void handleAck(JSONObject o) {
        int id = o.optInt("id", -1);
        boolean ok = o.optInt("ok", 0) == 1;
        String detail = o.optString("msg", ok ? "Concluído." : "O tênis recusou o comando.");
        finishCommand(id, ok, detail);
    }

    private void finishCommand(final int id, final boolean ok, final String detail) {
        final PendingCommand pc;
        synchronized (pendingCommands) { pc = pendingCommands.remove(id); }
        if (pc == null) return;
        if (pc.timeout != null) main.removeCallbacks(pc.timeout);
        addLog(ok ? BleEvent.INFO : BleEvent.WARN, "Comando " + pc.name + ": " + detail);
        if (pc.callback != null) main.post(() -> pc.callback.onResult(ok, detail));
    }
}
