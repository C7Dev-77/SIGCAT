package com.sigcat.dao;

import com.sigcat.dao.UsuarioDAO;
import com.sigcat.models.Usuario;
import com.sigcat.utils.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de UsuarioDAO, cubriendo los casos CP-09 a CP-12
 * de la matriz de casos de prueba del Login.
 *
 * Responsable: José Sánchez (QA)
 */
class UsuarioDAOTest {

    private static final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @BeforeAll
    static void inicializarBaseDeDatos() throws SQLException {
        // Garantiza que las tablas y los usuarios semilla (documento 1001 y 2002)
        // existan antes de correr las pruebas. Es seguro llamarlo varias veces.
        DatabaseInitializer.initialize();
    }

    @Test
    void autenticarConCredencialesValidasDebeRetornarUsuario() throws SQLException {
        // CP-09
        Optional<Usuario> resultado = usuarioDAO.autenticar("2002", "123");

        assertTrue(resultado.isPresent(), "Debería autenticar con credenciales válidas");
        assertEquals(Usuario.Rol.PROPIETARIO, resultado.get().getRol());
    }

    @Test
    void autenticarConPasswordIncorrectaDebeRetornarVacio() throws SQLException {
        // CP-10
        Optional<Usuario> resultado = usuarioDAO.autenticar("2002", "password_incorrecta");

        assertTrue(resultado.isEmpty(), "No debería autenticar con contraseña incorrecta");
    }

    @Test
    void autenticarConDocumentoInexistenteDebeRetornarVacio() throws SQLException {
        Optional<Usuario> resultado = usuarioDAO.autenticar("99999", "123");

        assertTrue(resultado.isEmpty(), "No debería autenticar con documento inexistente");
    }

    @Test
    void buscarPorIdExistenteDebeRetornarUsuario() throws SQLException {
        // CP-11 (id 1 = Carlos Pérez, Funcionario, según el seed de DatabaseInitializer)
        Optional<Usuario> resultado = usuarioDAO.buscarPorId(1);

        assertTrue(resultado.isPresent(), "Debería encontrar el usuario con id 1");
        assertEquals(Usuario.Rol.FUNCIONARIO, resultado.get().getRol());
    }

    @Test
    void buscarPorIdInexistenteDebeRetornarVacio() throws SQLException {
        // CP-12
        Optional<Usuario> resultado = usuarioDAO.buscarPorId(9999);

        assertTrue(resultado.isEmpty(), "No debería encontrar un usuario con id inexistente");
    }
}