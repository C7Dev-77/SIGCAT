package com.sigcat.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para FileValidator (Mejora #7: Validación de Tamaño y Magic Bytes).
 */
class FileValidatorTest {

    @Test
    void validarDocumentoPDFValido(@TempDir Path tempDir) throws IOException {
        File pdfFile = tempDir.resolve("test_escritura.pdf").toFile();
        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            // Escribir cabecera válida %PDF-
            fos.write(new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37});
            fos.write("Contenido de prueba PDF".getBytes());
        }

        FileValidator.ResultadoValidacion res = FileValidator.validarDocumento(pdfFile);
        assertTrue(res.valido(), "Un PDF con cabecera %PDF- válida debe ser aceptado");
    }

    @Test
    void rechazarDocumentoFalsoConExtensionPDF(@TempDir Path tempDir) throws IOException {
        File fakePdf = tempDir.resolve("virus.pdf").toFile();
        try (FileOutputStream fos = new FileOutputStream(fakePdf)) {
            // Escribir cabecera falsa que no es PDF
            fos.write("ESTO NO ES UN PDF".getBytes());
        }

        FileValidator.ResultadoValidacion res = FileValidator.validarDocumento(fakePdf);
        assertFalse(res.valido(), "Un archivo con extensión .pdf pero sin cabecera PDF debe ser rechazado");
        assertTrue(res.mensaje().contains("cabecera corrupta") || res.mensaje().contains("no es un documento PDF"));
    }

    @Test
    void rechazarArchivoInexistente() {
        File inexistente = new File("archivo_que_no_existe_999.pdf");
        FileValidator.ResultadoValidacion res = FileValidator.validarDocumento(inexistente);
        assertFalse(res.valido());
    }
}

