package com.sigcat.controllers;

import com.sigcat.dao.UsuarioDAO;
import com.sigcat.models.Usuario;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Controlador de la vista de Login.
 * Gestiona la autenticación y redirecciona según el rol del usuario.
 *
 * Responsable: Leider Barreto (UI/UX)
 */
public class LoginController {

    @FXML private TextField txtDocumento;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;
    @FXML private Button btnIngresar;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    /**
     * Acción del botón "Ingresar".
     * Valida campos y autentica al usuario contra la BD.
     */
    @FXML
    private void onIngresar() {
        lblError.setText("");

        String documento = txtDocumento.getText().trim();
        String password  = txtPassword.getText().trim();

        // ─── Validación de campos vacíos ──────────────────────────
        if (documento.isEmpty() || password.isEmpty()) {
            lblError.setText("Por favor, complete todos los campos.");
            return;
        }

        // ─── Autenticación ────────────────────────────────────────
        try {
            Optional<Usuario> resultado = usuarioDAO.autenticar(documento, password);

            if (resultado.isPresent()) {
                Usuario usuario = resultado.get();
                redirigirSegunRol(usuario);
            } else {
                lblError.setText("Documento o contraseña incorrectos.");
            }
        } catch (SQLException e) {
            lblError.setText("Error de base de datos. Contacte al administrador.");
            e.printStackTrace();
        }
    }

    /**
     * Redirige al panel correspondiente según el rol del usuario.
     * TODO: Leider implementa la navegación a las vistas de cada rol.
     */
    private void redirigirSegunRol(Usuario usuario) {
        if (usuario.getRol() == Usuario.Rol.FUNCIONARIO) {
            System.out.println("[LOGIN] Redirigiendo a panel Funcionario: " + usuario.getNombre());
            // TODO: cargar dashboard-funcionario.fxml
        } else {
            System.out.println("[LOGIN] Redirigiendo a panel Propietario: " + usuario.getNombre());
            // TODO: cargar dashboard-propietario.fxml
        }
    }
}
