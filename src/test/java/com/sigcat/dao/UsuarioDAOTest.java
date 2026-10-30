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
 * de la matriz de casos de prueba del Login y validación de autoregistro.
 *
 * Responsable: José Sánchez (QA)
 * Actualizado por: Cristian Morales (Arquitecto) — Pruebas BCrypt (RNF-01) y Autoregistro
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
    void autenticarConCredencialesValidasDebeRetornarUsuario() {
        // CP-09
        Usuario resultado = usuarioDAO.autenticar("2002", "123");

        assertNotNull(resultado, "Debería autenticar con credenciales válidas");
        assertEquals("PROPIETARIO", resultado.getRol());
    }

    @Test
    void autenticarConPasswordIncorrectaDebeRetornarVacio() {
        // CP-10
        Usuario resultado = usuarioDAO.autenticar("2002", "password_incorrecta");

        assertNull(resultado, "No debería autenticar con contraseña incorrecta");
    }

    @Test
    void autenticarConDocumentoInexistenteDebeRetornarVacio() {
        Usuario resultado = usuarioDAO.autenticar("99999", "123");

        assertNull(resultado, "No debería autenticar con documento inexistente");
    }

    @Test
    void buscarPorIdExistenteDebeRetornarUsuario() throws SQLException {
        // CP-11 (id 1 = Carlos Pérez, Funcionario, según el seed de DatabaseInitializer)
        Optional<Usuario> resultado = usuarioDAO.buscarPorId(1);

        assertTrue(resultado.isPresent(), "Debería encontrar el usuario con id 1");
        assertEquals("FUNCIONARIO", resultado.get().getRol());
    }

    @Test
    void buscarPorIdInexistenteDebeRetornarVacio() throws SQLException {
        // CP-12
        Optional<Usuario> resultado = usuarioDAO.buscarPorId(9999);

        assertTrue(resultado.isEmpty(), "No debería encontrar un usuario con id inexistente");
    }

    @Test
    void existeDocumentoDebeRetornarTrueParaUsuarioRegistrado() {
        assertTrue(usuarioDAO.existeDocumento("1001"), "Documento 1001 semilla debe existir");
        assertTrue(usuarioDAO.existeDocumento("2002"), "Documento 2002 semilla debe existir");
        assertFalse(usuarioDAO.existeDocumento("88888888"), "Documento inexistente debe retornar false");
    }

    @Test
    void registrarPropietarioNuevoYAutenticar() {
        String docUnico = "9900" + (System.currentTimeMillis() % 100000);
        int id = usuarioDAO.registrarPropietario(docUnico, "Propietario Test", "claveSegura123");
        assertTrue(id > 0, "El ID retornado debe ser positivo");

        Usuario auth = usuarioDAO.autenticar(docUnico, "claveSegura123");
        assertNotNull(auth, "El nuevo propietario debe poder autenticarse");
        assertEquals("Propietario Test", auth.getNombre());
        assertEquals("PROPIETARIO", auth.getRol());

        // Documento ya existe, registrar de nuevo debe retornar -1
        int idDuplicado = usuarioDAO.registrarPropietario(docUnico, "Otro Nombre", "otraClave");
        assertEquals(-1, idDuplicado, "No debe permitir documentos duplicados");
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
