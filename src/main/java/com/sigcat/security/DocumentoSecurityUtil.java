package com.sigcat.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utilidad criptográfica para la firma digital e integridad de documentos catastrales (RNF / Mejora #2).
 * Genera y valida firmas digitales y hashes criptográficos SHA-256 para prevenir
 * la adulteración de escrituras, recibos de validación y certificados de incorporación.
 */
public final class DocumentoSecurityUtil {

    private static final String ALGORITMO = "SHA-256";

    private DocumentoSecurityUtil() {
        // Clase de utilidades estática
    }

    /**
     * Calcula el hash SHA-256 de un arreglo de bytes en memoria.
     *
     * @param contenido Contenido binario del documento.
     * @return Cadena hexadecimal en minúsculas con el hash SHA-256 (64 caracteres).
     */
    public static String calcularSHA256(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITMO);
            byte[] hash = digest.digest(contenido);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo criptográfico SHA-256 no disponible en la JVM", e);
        }
    }

    /**
     * Calcula el hash SHA-256 leyendo un archivo en disco por bloques de memoria.
     *
     * @param archivo Archivo a analizar.
     * @return Cadena hexadecimal con el hash SHA-256.
     * @throws IOException Si ocurre un error de lectura en el archivo.
     */
    public static String calcularSHA256(File archivo) throws IOException {
        if (archivo == null || !archivo.exists() || !archivo.isFile()) {
            throw new IllegalArgumentException("El archivo especificado no existe o es inválido: " + archivo);
        }

        try (InputStream fis = new FileInputStream(archivo)) {
            MessageDigest digest = MessageDigest.getInstance(ALGORITMO);
            byte[] buffer = new byte[8192];
            int leidos;
            while ((leidos = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, leidos);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo criptográfico SHA-256 no disponible en la JVM", e);
        }
    }

    /**
     * Valida si el contenido de un archivo coincide exactamente con un hash SHA-256 esperado.
     *
     * @param archivo Archivo a verificar.
     * @param hashEsperado Hash SHA-256 registrado previamente.
     * @return true si el archivo no ha sido adulterado; false si difiere o si el hash es nulo.
     */
    public static boolean verificarIntegridad(File archivo, String hashEsperado) {
        if (hashEsperado == null || hashEsperado.trim().isEmpty() || archivo == null) {
            return false;
        }
        try {
            String hashCalculado = calcularSHA256(archivo);
            return hashCalculado.equalsIgnoreCase(hashEsperado.trim());
        } catch (IOException e) {
            System.err.println("[DocumentoSecurityUtil] Error al verificar integridad: " + e.getMessage());
            return false;
        }
    }

    /**
     * Valida si los bytes en memoria coinciden exactamente con un hash SHA-256 esperado.
     */
    public static boolean verificarIntegridad(byte[] contenido, String hashEsperado) {
        if (hashEsperado == null || hashEsperado.trim().isEmpty() || contenido == null) {
            return false;
        }
        String hashCalculado = calcularSHA256(contenido);
        return hashCalculado.equalsIgnoreCase(hashEsperado.trim());
    }
}

