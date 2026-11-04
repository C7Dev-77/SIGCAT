package com.sigcat.utils;

import com.sigcat.supabase.DatabaseConnectionSupabase;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Utilidad de limpieza de base de datos para sustentación y producción.
 * Elimina todas las peticiones, radicaciones de prueba, dictámenes y documentos de test,
 * preservando intactos los 11 predios públicos oficiales de Sincelejo y las cuentas de usuario.
 */
public class DatabaseCleaner {

    public static void main(String[] args) {
        System.out.println("====================================================");
        System.out.println("  INICIANDO LIMPIEZA DE PRUEBAS - SIGCAT SINCELEJO  ");
        System.out.println("====================================================\n");

        // 1. Limpiar base de datos local en carpeta raíz
        limpiarSqlite("sigcat.db");

        // 2. Limpiar base de datos local en carpeta ejecutable
        limpiarSqlite("ejecutable/sigcat.db");

        // 3. Limpiar base de datos Supabase en la nube
        limpiarSupabase();

        System.out.println("\n====================================================");
        System.out.println("  ✔ LIMPIEZA COMPLETADA CON ÉXITO");
        System.out.println("  Sistema listo para la sustentación y pruebas limpias.");
        System.out.println("====================================================");
    }

    public static void limpiarSqlite(String rutaDb) {
        File f = new File(rutaDb);
        if (!f.exists()) {
            System.out.println("[SQLITE] Archivo no encontrado: " + rutaDb + " (se omite)");
            return;
        }

        System.out.println("\n--- Limpiando SQLite Local: " + rutaDb + " ---");
        String url = "jdbc:sqlite:" + rutaDb;
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Contar solicitudes no públicas antes de borrar
            int countNoPublicos = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM predios WHERE codigo_catastral NOT LIKE '70001-PUB-%'")) {
                if (rs.next()) countNoPublicos = rs.getInt(1);
            }
            System.out.println("[SQLITE] Predios de prueba/solicitudes a eliminar: " + countNoPublicos);

            // 1. Borrar vértices de predios de prueba
            stmt.executeUpdate("""
                DELETE FROM vertices 
                WHERE id_predio IN (SELECT id_predio FROM predios WHERE codigo_catastral NOT LIKE '70001-PUB-%')
            """);

            // 2. Borrar documentos de prueba
            stmt.executeUpdate("""
                DELETE FROM documentos 
                WHERE predio_id IN (SELECT id_predio FROM predios WHERE codigo_catastral NOT LIKE '70001-PUB-%')
                   OR predio_id NOT IN (SELECT id_predio FROM predios WHERE codigo_catastral LIKE '70001-PUB-%')
            """);

            // 3. Borrar logs de auditoría de predios de prueba
            stmt.executeUpdate("""
                DELETE FROM audit_log 
                WHERE entidad_id NOT LIKE '70001-PUB-%' AND entidad = 'PREDIO'
            """);

            // 4. Borrar los predios de prueba
            int eliminados = stmt.executeUpdate("DELETE FROM predios WHERE codigo_catastral NOT LIKE '70001-PUB-%'");

            conn.commit();
            conn.setAutoCommit(true);

            // Ejecutar VACUUM para compactar el archivo en disco
            try {
                stmt.execute("VACUUM;");
            } catch (Exception ignored) {}

            // Contar predios públicos restantes
            int countPublicos = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM predios WHERE codigo_catastral LIKE '70001-PUB-%'")) {
                if (rs.next()) countPublicos = rs.getInt(1);
            }

            System.out.println("[SQLITE] ✔ Eliminados: " + eliminados + " predios de prueba.");
            System.out.println("[SQLITE] ✔ Predios públicos oficiales preservados: " + countPublicos);

            // Si hay duplicados de predios públicos por pruebas anteriores, desduplicar
            if (countPublicos > 11) {
                System.out.println("[SQLITE] Depurando duplicados de predios públicos...");
                stmt.executeUpdate("""
                    DELETE FROM vertices WHERE id_predio IN (
                        SELECT id_predio FROM predios p1
                        WHERE codigo_catastral LIKE '70001-PUB-%'
                          AND id_predio > (
                              SELECT MIN(id_predio) FROM predios p2
                              WHERE p2.codigo_catastral = p1.codigo_catastral
                          )
                    )
                """);
                stmt.executeUpdate("""
                    DELETE FROM predios
                    WHERE codigo_catastral LIKE '70001-PUB-%'
                      AND id_predio > (
                          SELECT MIN(id_predio) FROM predios p2
                          WHERE p2.codigo_catastral = predios.codigo_catastral
                      )
                """);
                stmt.execute("VACUUM;");
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM predios WHERE codigo_catastral LIKE '70001-PUB-%'")) {
                    if (rs.next()) countPublicos = rs.getInt(1);
                }
                System.out.println("[SQLITE] ✔ Desduplicación completada. Predios públicos únicos finales: " + countPublicos);
            }

            // Listar los predios finales
            System.out.println("[SQLITE] Lista de predios en " + rutaDb + ":");
            try (ResultSet rs = stmt.executeQuery("SELECT codigo_catastral, nombre, estado FROM predios ORDER BY codigo_catastral")) {
                while (rs.next()) {
                    System.out.println("   • " + rs.getString(1) + " - " + rs.getString(2) + " [" + rs.getString(3) + "]");
                }
            }

            // Listar usuarios finales
            System.out.println("[SQLITE] Usuarios activos en " + rutaDb + ":");
            try (ResultSet rs = stmt.executeQuery("SELECT documento, nombre, rol FROM usuarios ORDER BY id_usuario")) {
                while (rs.next()) {
                    System.out.println("   👤 " + rs.getString(1) + " | " + rs.getString(2) + " (" + rs.getString(3) + ")");
                }
            }

        } catch (Exception e) {
            System.err.println("[SQLITE] Error limpiando " + rutaDb + ": " + e.getMessage());
        }
    }

    public static void limpiarSupabase() {
        if (!DatabaseConnectionSupabase.isOnline()) {
            System.err.println("[SUPABASE] ⚠️ No hay conexión a internet o Supabase no disponible.");
            return;
        }

        System.out.println("\n--- Limpiando Supabase Cloud (PostgreSQL) ---");
        try (Connection conn = DatabaseConnectionSupabase.getConnection();
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Contar solicitudes no públicas en Supabase
            int countNoPublicos = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM public.predios WHERE codigo_catastral NOT LIKE '70001-PUB-%'")) {
                if (rs.next()) countNoPublicos = rs.getInt(1);
            }
            System.out.println("[SUPABASE] Predios de prueba/solicitudes a eliminar en la nube: " + countNoPublicos);

            // 1. Borrar documentos de prueba en Supabase
            stmt.executeUpdate("""
                DELETE FROM public.documentos 
                WHERE predio_id IN (SELECT id FROM public.predios WHERE codigo_catastral NOT LIKE '70001-PUB-%')
            """);

            // 2. Borrar vértices de predios de prueba
            stmt.executeUpdate("""
                DELETE FROM public.vertices 
                WHERE id_predio IN (SELECT id FROM public.predios WHERE codigo_catastral NOT LIKE '70001-PUB-%')
            """);

            // 3. Borrar logs de auditoría de predios de prueba
            stmt.executeUpdate("""
                DELETE FROM public.audit_log 
                WHERE entidad_id NOT LIKE '70001-PUB-%' AND entidad = 'PREDIO'
            """);

            // 4. Borrar predios de prueba en Supabase
            int eliminados = stmt.executeUpdate("DELETE FROM public.predios WHERE codigo_catastral NOT LIKE '70001-PUB-%'");

            conn.commit();
            conn.setAutoCommit(true);

            // Contar predios públicos oficiales restantes en Supabase
            int countPublicos = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM public.predios WHERE codigo_catastral LIKE '70001-PUB-%'")) {
                if (rs.next()) countPublicos = rs.getInt(1);
            }

            System.out.println("[SUPABASE] ✔ Eliminados en la nube: " + eliminados + " predios de prueba.");
            System.out.println("[SUPABASE] ✔ Predios públicos oficiales preservados en Supabase: " + countPublicos);

        } catch (Exception e) {
            System.err.println("[SUPABASE] Error limpiando Supabase: " + e.getMessage());
        }
    }
}
