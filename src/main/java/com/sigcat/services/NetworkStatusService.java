package com.sigcat.services;

import com.sigcat.supabase.DatabaseConnectionSupabase;
import com.sigcat.sync.SyncEngine;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Servicio de detección automática de conexión a Internet y Supabase (Mejora #10).
 * Monitorea el estado de red de manera no bloqueante. Al recuperar la conexión,
 * dispara automáticamente el motor de sincronización (SyncEngine) en segundo plano.
 */
public class NetworkStatusService {

    private static NetworkStatusService instance;

    private final BooleanProperty online = new SimpleBooleanProperty(false);
    private final StringProperty estadoTexto = new SimpleStringProperty("Verificando conexión...");
    private ScheduledExecutorService scheduler;
    private final SyncEngine syncEngine = new SyncEngine();

    private Consumer<Boolean> onCambioConexionListener;

    private NetworkStatusService() {}

    public static synchronized NetworkStatusService getInstance() {
        if (instance == null) {
            instance = new NetworkStatusService();
        }
        return instance;
    }

    public synchronized void iniciarMonitoreo(int intervaloSegundos) {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sigcat-network-monitor");
            t.setDaemon(true);
            return t;
        });

        // Ejecución inmediata y luego periódica
        scheduler.scheduleAtFixedRate(this::verificarConexion, 0, Math.max(intervaloSegundos, 5), TimeUnit.SECONDS);
    }

    public synchronized void detenerMonitoreo() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void verificarConexion() {
        boolean estabaOnline = online.get();
        boolean estaOnline = DatabaseConnectionSupabase.isOnline();

        Platform.runLater(() -> {
            online.set(estaOnline);
            if (estaOnline) {
                estadoTexto.set("🟢 En línea (Supabase Cloud)");
            } else {
                estadoTexto.set("🟠 Modo Local (SQLite Offline)");
            }
        });

        // Transición de Offline a Online: Sincronización automática inteligente
        if (!estabaOnline && estaOnline) {
            System.out.println("[NETWORK] Conexión a internet restablecida. Iniciando sincronización automática...");
            try {
                syncEngine.sincronizar();
            } catch (Exception e) {
                System.err.println("[NETWORK] Error en autosincronización: " + e.getMessage());
            }
        }

        if (onCambioConexionListener != null && estabaOnline != estaOnline) {
            Platform.runLater(() -> onCambioConexionListener.accept(estaOnline));
        }
    }

    public BooleanProperty onlineProperty() {
        return online;
    }

    public boolean isOnline() {
        return online.get();
    }

    public StringProperty estadoTextoProperty() {
        return estadoTexto;
    }

    public void setOnCambioConexionListener(Consumer<Boolean> listener) {
        this.onCambioConexionListener = listener;
    }
}
