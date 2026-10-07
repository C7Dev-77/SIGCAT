package com.sigcat.integration;

import com.sigcat.dao.PredioDAO;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.models.Predio;
import com.sigcat.models.Vertice;
import com.sigcat.utils.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba End-to-End (CP-24): verifica que un predio recién APROBADO
 * pase a afectar la validación de solapamiento de futuros registros.
 *
 * Simula el flujo completo: un propietario registra un predio, un
 * funcionario lo aprueba, y luego otro propietario intenta registrar
 * un predio que se cruza con el ya aprobado.
 *
 * Responsable: José Sánchez (QA)
 */
class FlujoAprobacionIntegrationTest {

    private static final PredioDAO predioDAO = new PredioDAO();

    @BeforeAll
    static void inicializarBaseDeDatos() throws SQLException {
        DatabaseInitializer.initialize();
    }

    @Test
    void predioRecienAprobadoDebeDetectarseEnNuevoRegistroSolapado() throws SQLException {
        // ─── Paso 1: Propietario A registra un predio (queda PENDIENTE) ───
        Predio predioA = new Predio(2); // id_propietario = 2 (usuario semilla)
        predioA.setAreaCalculada(100.0);
        predioA.setVertices(List.of(
            new Vertice(0, 1, 9000, 9000),
            new Vertice(0, 2, 9010, 9000),
            new Vertice(0, 3, 9010, 9010),
            new Vertice(0, 4, 9000, 9010)
        ));
        int idPredioA = predioDAO.insertarConVertices(predioA);

        // ─── Paso 2: Mientras está PENDIENTE, NO debe afectar la validación ───
        List<Predio> aprobadosAntes = predioDAO.listarAprobadosConVertices();
        assertTrue(
            aprobadosAntes.stream().noneMatch(p -> p.getId() == idPredioA),
            "Un predio PENDIENTE no debe aparecer todavía como aprobado"
        );

        // ─── Paso 3: Funcionario aprueba el predio ───
        predioDAO.actualizarEstado(idPredioA, Predio.Estado.APROBADO, 1); // id_funcionario = 1

        // ─── Paso 4: Propietario B intenta registrar un predio que se cruza con A ───
        List<Predio> aprobadosDespues = predioDAO.listarAprobadosConVertices();
        List<Polygon> poligonosAprobados = aprobadosDespues.stream()
            .map(p -> SolapamientoValidator.construirPoligono(p.getVertices()))
            .toList();

        List<Vertice> verticesPredioB = List.of(
            new Vertice(0, 1, 9005, 9005), // se cruza con el área de A (9000-9010)
            new Vertice(0, 2, 9015, 9005),
            new Vertice(0, 3, 9015, 9015),
            new Vertice(0, 4, 9005, 9015)
        );
        Polygon poligonoB = SolapamientoValidator.construirPoligono(verticesPredioB);

        boolean solapa = SolapamientoValidator.existeSolapamiento(poligonoB, poligonosAprobados);

        // ─── Resultado esperado: el sistema SÍ debe detectar el conflicto ───
        assertTrue(solapa,
            "Un predio que se cruza con uno recién aprobado debe detectarse como solapamiento");
    }
}