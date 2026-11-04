package com.sigcat.supabase;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Cliente WebSocket para suscripciones Push en tiempo real de Supabase Realtime (Mejora #1).
 * Implementa el protocolo Phoenix Channels de Supabase sobre java.net.http.WebSocket nativo.
 * Permite escuchar inserciones, cambios de estado y radicaciones sin necesidad de sondeo (polling).
 */
public class SupabaseRealtimeService implements WebSocket.Listener {

    private static SupabaseRealtimeService instance;

    private final String supabaseUrl;
    private final String anonKey;
    private final HttpClient httpClient;
    private WebSocket webSocket;
    private ScheduledExecutorService heartbeatExecutor;

    private final List<BiConsumer<String, JsonObject>> listenersPredios = new ArrayList<>();
    private final List<BiConsumer<String, JsonObject>> listenersAuditoria = new ArrayList<>();
    private final StringBuilder bufferMensaje = new StringBuilder();

    private boolean conectado = false;

    private SupabaseRealtimeService() {
        this.supabaseUrl = SupabaseConfig.getSupabaseUrl();
        this.anonKey = SupabaseConfig.getAnonKey();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static synchronized SupabaseRealtimeService getInstance() {
        if (instance == null) {
            instance = new SupabaseRealtimeService();
        }
        return instance;
    }

    /**
     * Inicia la conexión WebSocket con Supabase Realtime.
     */
    public synchronized void conectar() {
        if (conectado || supabaseUrl == null || anonKey == null) {
            return;
        }

        try {
            // Transformar https://xyz.supabase.co a wss://xyz.supabase.co/realtime/v1/websocket
            String wsBase = supabaseUrl.replace("https://", "wss://").replace("http://", "ws://");
            String wsEndpoint = wsBase + "/realtime/v1/websocket?apikey=" + anonKey + "&vsn=1.0.0";

            httpClient.newWebSocketBuilder()
                    .header("apikey", anonKey)
                    .buildAsync(URI.create(wsEndpoint), this)
                    .thenAccept(ws -> {
                        this.webSocket = ws;
                        this.conectado = true;
                        System.out.println("[REALTIME] WebSocket conectado con Supabase Realtime.");
                        iniciarHeartbeat();
                        suscribirCanales();
                    })
                    .exceptionally(ex -> {
                        System.err.println("[REALTIME] Aviso: No se pudo conectar a Realtime (modo offline): " + ex.getMessage());
                        this.conectado = false;
                        return null;
                    });
        } catch (Exception e) {
            System.err.println("[REALTIME] Error al inicializar WebSocket: " + e.getMessage());
        }
    }

    private void iniciarHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdownNow();
        }

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sigcat-realtime-heartbeat");
            t.setDaemon(true);
            return t;
        });

        // Enviar heartbeat de Phoenix Channels cada 25 segundos
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (webSocket != null && conectado) {
                String heartbeatMsg = "{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":\"hb\"}";
                webSocket.sendText(heartbeatMsg, true);
            }
        }, 20, 25, TimeUnit.SECONDS);
    }

    private void suscribirCanales() {
        if (webSocket == null || !conectado) return;

        // 1. Suscripción a cambios en la tabla 'predios'
        String joinPredios = """
            {
                "topic": "realtime:public:predios",
                "event": "phx_join",
                "payload": {
                    "config": {
                        "postgres_changes": [
                            {"event": "*", "schema": "public", "table": "predios"}
                        ]
                    }
                },
                "ref": "join_predios"
            }
        """;
        webSocket.sendText(joinPredios, true);

        // 2. Suscripción a cambios en la tabla 'audit_log'
        String joinAudit = """
            {
                "topic": "realtime:public:audit_log",
                "event": "phx_join",
                "payload": {
                    "config": {
                        "postgres_changes": [
                            {"event": "*", "schema": "public", "table": "audit_log"}
                        ]
                    }
                },
                "ref": "join_audit"
            }
        """;
        webSocket.sendText(joinAudit, true);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        bufferMensaje.append(data);
        if (last) {
            String mensaje = bufferMensaje.toString();
            bufferMensaje.setLength(0);
            procesarMensajeRealtime(mensaje);
        }
        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    private void procesarMensajeRealtime(String mensaje) {
        try {
            JsonObject json = JsonParser.parseString(mensaje).getAsJsonObject();
            String event = json.has("event") ? json.get("event").getAsString() : "";
            String topic = json.has("topic") ? json.get("topic").getAsString() : "";

            if ("postgres_changes".equals(event) && json.has("payload")) {
                JsonObject payload = json.getAsJsonObject("payload");
                JsonObject data = payload.has("data") ? payload.getAsJsonObject("data") : null;
                if (data != null) {
                    String tipoCambio = data.has("type") ? data.get("type").getAsString() : "UPDATE"; // INSERT, UPDATE, DELETE
                    JsonObject registro = data.has("record") ? data.getAsJsonObject("record") : new JsonObject();

                    if (topic.contains("predios")) {
                        Platform.runLater(() -> {
                            for (var listener : listenersPredios) {
                                listener.accept(tipoCambio, registro);
                            }
                        });
                    } else if (topic.contains("audit_log")) {
                        Platform.runLater(() -> {
                            for (var listener : listenersAuditoria) {
                                listener.accept(tipoCambio, registro);
                            }
                        });
                    }
                }
            }
        } catch (Exception ignored) {
            // Mensaje informativo o no estructurado de Phoenix
        }
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("[REALTIME] Error en canal WebSocket: " + error.getMessage());
        this.conectado = false;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("[REALTIME] WebSocket desconectado (" + statusCode + "): " + reason);
        this.conectado = false;
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    public synchronized void desconectar() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Cierre voluntario");
            } catch (Exception ignored) {}
        }
        this.conectado = false;
    }

    public void registrarListenerPredios(BiConsumer<String, JsonObject> listener) {
        this.listenersPredios.add(listener);
    }

    public void registrarListenerAuditoria(BiConsumer<String, JsonObject> listener) {
        this.listenersAuditoria.add(listener);
    }

    public boolean isConectado() {
        return conectado;
    }
}

