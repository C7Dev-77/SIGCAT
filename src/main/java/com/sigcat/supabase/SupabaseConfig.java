package com.sigcat.supabase;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.File;

/**
 * Gestor centralizado y resiliente de configuración para Supabase.
 * Busca las credenciales en orden de prioridad:
 * 1. Archivo .env en el directorio de ejecución actual.
 * 2. Archivo .env en el directorio padre (para ejecuciones desde subdirectorios como ejecutable/).
 * 3. Variables de entorno del sistema operativo (System.getenv).
 * 4. Valores por defecto preconfigurados del proyecto SIGCAT Sincelejo.
 */
public class SupabaseConfig {

    private static final String DEFAULT_URL = "https://jrgawunaqyhwahfavfeg.supabase.co";
    private static final String DEFAULT_ANON_KEY = "sb_publishable_p2hZlHGFmnLftO11fvlTow_i5nC5Qun";
    private static final String DEFAULT_JDBC_URL = "jdbc:postgresql://aws-0-us-east-2.pooler.supabase.com:5432/postgres?sslmode=require";
    private static final String DEFAULT_DB_USER = "postgres.jrgawunaqyhwahfavfeg";
    private static final String DEFAULT_DB_PASSWORD = "ftNndOGORefvXTKL";

    private static Dotenv dotenv;

    static {
        try {
            Dotenv d = Dotenv.configure().ignoreIfMissing().load();
            if (d.get("SUPABASE_URL") == null) {
                // Intentar en directorio padre
                File parentEnv = new File("../.env");
                if (parentEnv.exists()) {
                    d = Dotenv.configure().directory("..").ignoreIfMissing().load();
                }
            }
            dotenv = d;
        } catch (Exception e) {
            System.err.println("[CONFIG] Aviso al inicializar Dotenv: " + e.getMessage());
        }
    }

    public static String get(String key) {
        String val = null;
        if (dotenv != null) {
            try {
                val = dotenv.get(key);
            } catch (Exception ignored) {}
        }
        if (val == null || val.isBlank()) {
            val = System.getenv(key);
        }
        if (val == null || val.isBlank()) {
            val = switch (key) {
                case "SUPABASE_URL" -> DEFAULT_URL;
                case "SUPABASE_ANON_KEY" -> DEFAULT_ANON_KEY;
                case "SUPABASE_JDBC_URL" -> DEFAULT_JDBC_URL;
                case "SUPABASE_DB_USER" -> DEFAULT_DB_USER;
                case "SUPABASE_DB_PASSWORD" -> DEFAULT_DB_PASSWORD;
                default -> null;
            };
        }
        return val;
    }

    public static String getSupabaseUrl() {
        return get("SUPABASE_URL");
    }

    public static String getAnonKey() {
        return get("SUPABASE_ANON_KEY");
    }

    public static String getJdbcUrl() {
        return get("SUPABASE_JDBC_URL");
    }

    public static String getDbUser() {
        return get("SUPABASE_DB_USER");
    }

    public static String getDbPassword() {
        return get("SUPABASE_DB_PASSWORD");
    }
}

