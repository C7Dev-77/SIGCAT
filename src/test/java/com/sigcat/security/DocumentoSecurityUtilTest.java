package com.sigcat.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para DocumentoSecurityUtil (Firma e Integridad Criptográfica SHA-256).
 */
class DocumentoSecurityUtilTest {

    @Test
    void calcularSHA256DeBytesDebeGenerarHashCorrecto() {
        byte[] data = "SIGCAT Sincelejo 2026 - Certificado Catastral".getBytes();
        String hash = DocumentoSecurityUtil.calcularSHA256(data);

        assertNotNull(hash);
        assertEquals(64, hash.length(), "Un hash SHA-256 debe tener exactamente 64 caracteres hexadecimales");

        // Hash determinístico
        String hash2 = DocumentoSecurityUtil.calcularSHA256(data);
        assertEquals(hash, hash2, "Mismo contenido debe producir idéntico hash SHA-256");
    }

    @Test
    void verificarIntegridadDeArchivo(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("recibo_test.pdf");
        Files.writeString(tempFile, "Contenido ficticio de recibo catastral");

        File file = tempFile.toFile();
        String hashOriginal = DocumentoSecurityUtil.calcularSHA256(file);

        assertTrue(DocumentoSecurityUtil.verificarIntegridad(file, hashOriginal),
                "El archivo intacto debe verificar positivamente");

        // Modificar archivo para simular adulteración
        Files.writeString(tempFile, "Contenido adulterado maliciosamente");
        assertFalse(DocumentoSecurityUtil.verificarIntegridad(file, hashOriginal),
                "Un archivo adulterado debe ser detectado y rechazado");
    }
}

