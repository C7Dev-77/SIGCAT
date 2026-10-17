package com.sigcat.dao;

import com.sigcat.dao.UsuarioDAO;
import com.sigcat.models.Usuario;
import com.sigcat.security.PasswordUtil;
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
 * Actualizado por: Cristian Morales (Arquitecto) — Pruebas BCrypt (RNF-01)
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

    // ─── Pruebas nuevas para BCrypt (RNF-01) ───

    @Test
    void hashearPasswordDebeProducirHashDiferenteCadaVez() {
        // Verifica que BCrypt usa salt aleatorio: dos hashes del mismo texto son distintos
        String password = "123";
        String hash1 = PasswordUtil.hashPassword(password);
        String hash2 = PasswordUtil.hashPassword(password);

        assertNotEquals(hash1, hash2, "BCrypt debe generar hashes distintos cada vez (salt aleatorio)");
        // Pero ambos deben validar correctamente contra la misma contraseña
        assertTrue(PasswordUtil.checkPassword(password, hash1), "hash1 debe validar correctamente");
        assertTrue(PasswordUtil.checkPassword(password, hash2), "hash2 debe validar correctamente");
    }

    @Test
    void checkPasswordCorrectoDebeRetornarTrue() {
        // Verifica que PasswordUtil valida correctamente una contraseña contra su hash
        String password = "miPasswordSegura123";
        String hash = PasswordUtil.hashPassword(password);

        assertTrue(PasswordUtil.checkPassword(password, hash),
            "checkPassword debe retornar true para contraseña correcta");
    }

    @Test
    void checkPasswordIncorrectoDebeRetornarFalse() {
        // Verifica el rechazo de contraseña incorrecta
        String password = "passwordCorrecta";
        String wrongPassword = "passwordIncorrecta";
        String hash = PasswordUtil.hashPassword(password);

        assertFalse(PasswordUtil.checkPassword(wrongPassword, hash),
            "checkPassword debe retornar false para contraseña incorrecta");
    }
}