package com.sigcat.dao;

import com.sigcat.models.Predio;
import com.sigcat.models.Vertice;
import com.sigcat.utils.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de PredioDAO, cubriendo los casos CP-25 a CP-28
 * de la matriz de casos de prueba de Registro y Aprobación de Predios.
 *
 * Responsable: José Sánchez (QA)
 */
class PredioDAOTest {

    private static final PredioDAO predioDAO = new PredioDAO();

    @BeforeAll
    static void inicializarBaseDeDatos() throws SQLException {
        DatabaseInitializer.initialize();
    }

    /**
     * Crea un predio de prueba con un cuadrado simple, asociado al
     * propietario semilla (id 2, Ana Gómez).
     */
    private Predio crearPredioDePrueba(double offsetX, double offsetY) {
        Predio predio = new Predio(2); // id_propietario = 2 (usuario semilla)
        predio.setAreaCalculada(100.0);
        predio.setVertices(List.of(
            new Vertice(0, 1, offsetX, offsetY),
            new Vertice(0, 2, offsetX + 10, offsetY),
            new Vertice(0, 3, offsetX + 10, offsetY + 10),
            new Vertice(0, 4, offsetX, offsetY + 10)
        ));
        return predio;
    }

    @Test
    void insertarConVerticesDebeGuardarPredioYVertices() throws SQLException {
        // CP-25
        Predio predio = crearPredioDePrueba(1000, 1000); // zona alejada para no chocar con otras pruebas

        int idGenerado = predioDAO.insertarConVertices(predio);

        assertTrue(idGenerado > 0, "Debe retornar un id válido mayor a 0");

        VerticeDAO verticeDAO = new VerticeDAO();
        List<Vertice> verticesGuardados = verticeDAO.obtenerPorPredio(idGenerado);
        assertEquals(4, verticesGuardados.size(), "Debe haber guardado los 4 vértices del polígono");
    }

    @Test
    void listarPendientesSoloDebeIncluirEstadoPendiente() throws SQLException {
        // CP-26
        Predio predio = crearPredioDePrueba(2000, 2000);
        int id = predioDAO.insertarConVertices(predio); // queda PENDIENTE por defecto

        List<Predio> pendientes = predioDAO.listarPendientes();

        assertTrue(
            pendientes.stream().anyMatch(p -> p.getId() == id),
            "El predio recién insertado debe aparecer en la lista de pendientes"
        );
        assertTrue(
            pendientes.stream().allMatch(p -> p.getEstado() == Predio.Estado.PENDIENTE),
            "Todos los predios listados deben estar en estado PENDIENTE"
        );
    }

    @Test
    void actualizarEstadoDebeCambiarAPrrobadoYAsignarFuncionario() throws SQLException {
        // CP-28
        Predio predio = crearPredioDePrueba(3000, 3000);
        int id = predioDAO.insertarConVertices(predio);

        predioDAO.actualizarEstado(id, Predio.Estado.APROBADO, 1); // id_funcionario = 1 (usuario semilla)

        List<Predio> aprobados = predioDAO.listarAprobadosConVertices();
        Predio actualizado = aprobados.stream()
            .filter(p -> p.getId() == id)
            .findFirst()
            .orElseThrow(() -> new AssertionError("El predio debería aparecer como APROBADO"));

        assertEquals(Predio.Estado.APROBADO, actualizado.getEstado());
        assertEquals(1, actualizado.getIdFuncionario());
    }

    @Test
    void listarAprobadosConVerticesDebeTraerLosVertices() throws SQLException {
        // CP-27
        Predio predio = crearPredioDePrueba(4000, 4000);
        int id = predioDAO.insertarConVertices(predio);
        predioDAO.actualizarEstado(id, Predio.Estado.APROBADO, 1);

        List<Predio> aprobados = predioDAO.listarAprobadosConVertices();
        Predio encontrado = aprobados.stream()
            .filter(p -> p.getId() == id)
            .findFirst()
            .orElseThrow();

        assertNotNull(encontrado.getVertices(), "Los vértices no deberían ser null");
        assertEquals(4, encontrado.getVertices().size(), "Debe traer los 4 vértices del predio aprobado");
    }
}