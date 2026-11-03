package com.sigcat.services;

import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.models.Vertice;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para CertificadoCatastralService (Mejoras #3 y #9: PDF Oficial y Código QR).
 */
class CertificadoCatastralServiceTest {

    @Test
    void generarCodigoQRPNGDebeRetornarBytesValidos() throws Exception {
        byte[] qrBytes = CertificadoCatastralService.generarCodigoQRPNG("https://sigcat.sincelejo.gov.co/validar?test=123", 150, 150);
        assertNotNull(qrBytes);
        assertTrue(qrBytes.length > 50, "Los bytes del código QR deben ser mayores a 50 bytes");
        // Verificar que empieza con la firma PNG (0x89, 'P', 'N', 'G')
        assertEquals((byte) 0x89, qrBytes[0]);
        assertEquals((byte) 'P', qrBytes[1]);
        assertEquals((byte) 'N', qrBytes[2]);
        assertEquals((byte) 'G', qrBytes[3]);
    }

    @Test
    void generarCertificadoPDFBytesDebeProducirPDFValido() throws IOException {
        CertificadoCatastralService service = new CertificadoCatastralService();

        Predio predio = new Predio(2);
        predio.setId(101);
        predio.setCodigoCatastral("70001-01-00-0001-0025-000");
        predio.setNombre("Finca La Esperanza - Sincelejo");
        predio.setDireccion("Vereda Chochó Km 4");
        predio.setUsoSuelo("RESIDENCIAL");
        predio.setTipo("PRIVADO");
        predio.setAreaCalculada(12450.75);
        predio.setVertices(List.of(
            new Vertice(101, 1, -75.3885, 9.3086),
            new Vertice(101, 2, -75.3875, 9.3086),
            new Vertice(101, 3, -75.3875, 9.3094),
            new Vertice(101, 4, -75.3885, 9.3094)
        ));

        Usuario funcionario = new Usuario("1001", "Carlos Pérez", "123", "FUNCIONARIO");
        funcionario.setId(1);
        Usuario propietario = new Usuario("2002", "Ana Gómez", "123", "PROPIETARIO");
        propietario.setId(2);

        byte[] pdfBytes = service.generarCertificadoPDFBytes(predio, funcionario, propietario);

        assertNotNull(pdfBytes, "El arreglo de bytes del PDF no debe ser nulo");
        assertTrue(pdfBytes.length > 1000, "El PDF generado debe contener suficiente información (> 1KB)");

        // Verificar que Apache PDFBox pueda abrir y parsear el documento sin errores
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            assertEquals(1, doc.getNumberOfPages(), "El certificado oficial debe constar de 1 página formal");
            assertNotNull(doc.getPage(0));
        }
    }

    @Test
    void exportarCertificadoAArchivoFisico(@TempDir Path tempDir) throws IOException {
        CertificadoCatastralService service = new CertificadoCatastralService();
        Predio predio = new Predio(2);
        predio.setId(102);
        predio.setCodigoCatastral("70001-TEST");
        predio.setAreaCalculada(500.0);

        File destino = tempDir.resolve("certificado_prueba.pdf").toFile();
        File generado = service.exportarCertificadoAArchivo(predio, null, null, destino);

        assertTrue(generado.exists(), "El archivo PDF debe existir en el disco");
        assertTrue(generado.length() > 500, "El archivo debe tener contenido");
    }
}
