package com.sigcat.dao;

import com.sigcat.models.DocumentoLegal;
import com.sigcat.models.Predio;
import com.sigcat.models.Vertice;
import com.sigcat.utils.DatabaseConnection;
import com.sigcat.utils.DatabaseInitializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para DocumentoDAO (Fase 6: Centro Documental Legal y Resiliencia Offline).
 */
class DocumentoDAOTest {

    private static DocumentoDAO documentoDAO;
    private static PredioDAO predioDAO;

    @BeforeAll
    static void setUp() throws SQLException {
        System.setProperty("sigcat.db", "target/sigcat_test.db");
        DatabaseConnection.reiniciarConexion();
        DatabaseInitializer.initialize();
        documentoDAO = new DocumentoDAO();
        predioDAO = new PredioDAO();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        DatabaseConnection.reiniciarConexion();
    }

    @Test
    void guardarYConsultarDocumentosPorPredio() throws SQLException {
        // 1. Crear predio real para cumplir con la restricción de clave foránea en SQLite
        Predio predio = new Predio(2);
        predio.setNombre("Predio Test Documental");
        predio.setAreaCalculada(150.0);
        predio.setVertices(List.of(
            new Vertice(0, 1, 3000, 3000),
            new Vertice(0, 2, 3010, 3000),
            new Vertice(0, 3, 3010, 3010),
            new Vertice(0, 4, 3000, 3010)
        ));
        int predioId = predioDAO.insertarConVertices(predio);
        assertTrue(predioId > 0, "El predio debe insertarse exitosamente");

        // 2. Crear documento legal
        String idDoc = UUID.randomUUID().toString();
        DocumentoLegal doc = new DocumentoLegal();
        doc.setId(idDoc);
        doc.setPredioId(predioId);
        doc.setPropietarioId("2");
        doc.setTipoDocumento("ESCRITURA_PUBLICA");
        doc.setNombreArchivo("escritura_notaria1_sincelejo.pdf");
        doc.setStoragePath("documentos-legales/" + idDoc + ".pdf");
        doc.setStorageUrl("https://supabase.co/storage/v1/object/public/documentos-legales/doc_test.pdf");
        doc.setDescripcion("Escritura Pública Notaría 1 de Sincelejo");
        doc.setEstado("APROBADO");

        boolean insertado = documentoDAO.insertar(doc);
        assertTrue(insertado, "El documento legal debe insertarse exitosamente");

        // 3. Consultar documentos
        List<DocumentoLegal> docs = documentoDAO.listarPorPredio(predioId);
        assertNotNull(docs, "La lista de documentos no debe ser nula");
        assertFalse(docs.isEmpty(), "Debe contener al menos el documento guardado");

        DocumentoLegal recuperado = docs.stream()
                .filter(d -> idDoc.equals(d.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(recuperado, "El documento insertado debe poder ser recuperado");
        assertEquals("ESCRITURA_PUBLICA", recuperado.getTipoDocumento());
        assertEquals("escritura_notaria1_sincelejo.pdf", recuperado.getNombreArchivo());

        // 4. Probar eliminación
        boolean eliminado = documentoDAO.eliminar(idDoc);
        assertTrue(eliminado, "El documento debe eliminarse correctamente");

        List<DocumentoLegal> docsDespues = documentoDAO.listarPorPredio(predioId);
        boolean existe = docsDespues.stream().anyMatch(d -> idDoc.equals(d.getId()));
        assertFalse(existe, "El documento eliminado ya no debe aparecer en la lista");
    }
}
