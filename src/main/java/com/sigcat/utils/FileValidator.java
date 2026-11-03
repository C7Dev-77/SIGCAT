package com.sigcat.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Validador estricto de archivos antes de interactuar con Supabase Storage (Mejora #7).
 * Verifica existencia, límites de cuota (10 MB documentos, 5 MB avatares), extensiones
 * permitidas y números mágicos binarios (Magic Bytes) para evitar subidas maliciosas o corruptas.
 */
public final class FileValidator {

    public static final long MAX_DOCUMENTO_BYTES = 10 * 1024 * 1024; // 10 MB
    public static final long MAX_AVATAR_BYTES = 5 * 1024 * 1024;     // 5 MB

    private FileValidator() {}

    public record ResultadoValidacion(boolean valido, String mensaje) {
        public static ResultadoValidacion ok() {
            return new ResultadoValidacion(true, "Archivo válido");
        }
        public static ResultadoValidacion error(String mensaje) {
            return new ResultadoValidacion(false, mensaje);
        }
    }

    /**
     * Valida documentos legales (PDF o imágenes JPG/PNG).
     */
    public static ResultadoValidacion validarDocumento(File archivo) {
        if (archivo == null || !archivo.exists() || !archivo.isFile()) {
            return ResultadoValidacion.error("El archivo seleccionado no existe o no es accesible.");
        }

        if (archivo.length() == 0) {
            return ResultadoValidacion.error("El archivo seleccionado está vacío (0 bytes).");
        }

        if (archivo.length() > MAX_DOCUMENTO_BYTES) {
            long megas = archivo.length() / (1024 * 1024);
            return ResultadoValidacion.error(
                String.format(Locale.ROOT, "El archivo pesa %d MB y excede el límite máximo permitido de 10 MB.", megas)
            );
        }

        String nombre = archivo.getName().toLowerCase(Locale.ROOT);
        if (!nombre.endsWith(".pdf") && !nombre.endsWith(".png") && !nombre.endsWith(".jpg") && !nombre.endsWith(".jpeg")) {
            return ResultadoValidacion.error("Formato no permitido. Solo se aceptan documentos PDF o imágenes JPG/PNG.");
        }

        // Inspección de cabecera binaria (Magic Bytes)
        try (FileInputStream fis = new FileInputStream(archivo)) {
            byte[] header = new byte[8];
            int leidos = fis.read(header);
            if (leidos < 4) {
                return ResultadoValidacion.error("El archivo es demasiado pequeño o está dañado.");
            }

            if (nombre.endsWith(".pdf")) {
                // PDF Header: %PDF-
                if (header[0] != 0x25 || header[1] != 0x50 || header[2] != 0x44 || header[3] != 0x46) {
                    return ResultadoValidacion.error("El archivo no es un documento PDF válido (cabecera corrupta).");
                }
            } else if (nombre.endsWith(".png")) {
                // PNG Header: 0x89 50 4E 47
                if ((header[0] & 0xFF) != 0x89 || header[1] != 0x50 || header[2] != 0x4E || header[3] != 0x47) {
                    return ResultadoValidacion.error("El archivo no es una imagen PNG válida.");
                }
            } else if (nombre.endsWith(".jpg") || nombre.endsWith(".jpeg")) {
                // JPEG Header: 0xFF 0xD8 0xFF
                if ((header[0] & 0xFF) != 0xFF || (header[1] & 0xFF) != 0xD8 || (header[2] & 0xFF) != 0xFF) {
                    return ResultadoValidacion.error("El archivo no es una imagen JPEG/JPG válida.");
                }
            }
        } catch (IOException e) {
            return ResultadoValidacion.error("Error al leer el archivo: " + e.getMessage());
        }

        return ResultadoValidacion.ok();
    }

    /**
     * Valida imágenes de avatar de usuario (JPG o PNG, máx 5 MB).
     */
    public static ResultadoValidacion validarAvatar(File archivo) {
        if (archivo == null || !archivo.exists() || !archivo.isFile()) {
            return ResultadoValidacion.error("El archivo de avatar no existe.");
        }

        if (archivo.length() > MAX_AVATAR_BYTES) {
            return ResultadoValidacion.error("El avatar excede el tamaño máximo permitido de 5 MB.");
        }

        String nombre = archivo.getName().toLowerCase(Locale.ROOT);
        if (!nombre.endsWith(".png") && !nombre.endsWith(".jpg") && !nombre.endsWith(".jpeg")) {
            return ResultadoValidacion.error("El avatar debe ser una imagen en formato PNG o JPG.");
        }

        return ResultadoValidacion.ok();
    }
}

