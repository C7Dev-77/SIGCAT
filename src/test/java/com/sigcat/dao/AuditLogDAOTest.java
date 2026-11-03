package com.sigcat.dao;

import com.sigcat.models.AuditLog;
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
 * Pruebas unitarias para AuditLogDAO (Mejora #4: Historial de Auditoría Legal).
 */
class AuditLogDAOTest {

    private static AuditLogDAO auditLogDAO;

    @BeforeAll
    static void setUp() throws SQLException {
        System.setProperty("sigcat.db", "target/sigcat_test.db");
        DatabaseConnection.reiniciarConexion();
        DatabaseInitializer.initialize();
        auditLogDAO = new AuditLogDAO();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        DatabaseConnection.reiniciarConexion();
    }

    @Test
    void registrarYConsultarLogsDeAuditoria() {
        String logId = UUID.randomUUID().toString();
        AuditLog log = new AuditLog(
            "PREDIO",
            "70001-TEST-AUDIT",
            "APROBACION",
            "PENDIENTE",
            "APROBADO",
            "1",
            "Carlos Pérez (Funcionario)",
            "Predio aprobado tras revisión de títulos y topografía"
        );
        log.setId(logId);

        boolean registrado = auditLogDAO.registrar(log);
        assertTrue(registrado, "El log de auditoría debe registrarse exitosamente");

        List<AuditLog> recientes = auditLogDAO.listarRecientes(20);
        assertNotNull(recientes);
        assertFalse(recientes.isEmpty(), "Debe existir al menos un registro en la auditoría");

        boolean encontrado = recientes.stream().anyMatch(l -> logId.equals(l.getId()));
        assertTrue(encontrado, "El registro de auditoría recién insertado debe aparecer en la lista");
    }
}

