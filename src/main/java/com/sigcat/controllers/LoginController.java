package com.sigcat.controllers;

import com.sigcat.dao.UsuarioDAO;
import com.sigcat.models.Usuario;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
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
     * Valida campos y autentica al usuario contra la BD SQLite.
     */
    @FXML
    private void onIngresar(ActionEvent event) {
        lblError.setText("");

        String documento = txtDocumento.getText().trim();
        String password  = txtPassword.getText().trim();

        // ─── Validación de campos vacíos ──────────────────────────
        if (documento.isEmpty() || password.isEmpty()) {
            lblError.setText("Por favor, complete todos los campos.");
            return;
        }

        // ─── Autenticación contra SQLite ──────────────────────────
        try {
            Optional<Usuario> resultado = usuarioDAO.autenticar(documento, password);

            if (resultado.isPresent()) {
                rutearSegunRol(resultado.get(), event);
            } else {
                lblError.setText("Documento o contraseña incorrectos.");
            }
        } catch (SQLException e) {
            lblError.setText("Error de base de datos. Contacte al administrador.");
            e.printStackTrace();
        }
    }

    /**
     * Carga la vista correspondiente al rol del usuario autenticado
     * y transfiere el objeto Usuario al controlador de destino.
     */
    private void rutearSegunRol(Usuario usuario, ActionEvent event) {
        try {
            String vistaFxml = switch (usuario.getRol()) {
                case PROPIETARIO -> "/fxml/menu-propietario.fxml";
                case FUNCIONARIO -> "/fxml/menu-funcionario.fxml";
            };

            FXMLLoader loader = new FXMLLoader(getClass().getResource(vistaFxml));
            Parent root = loader.load();

            // Si el controlador implementa RecibeUsuario, le pasamos el usuario.
            Object controller = loader.getController();
            if (controller instanceof RecibeUsuario recibeUsuario) {
                recibeUsuario.setUsuario(usuario);
            }

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            Scene escena = new Scene(root, 800, 600);
            escena.getStylesheets().add(
                getClass().getResource("/styles/main.css").toExternalForm()
            );
            stage.setScene(escena);
            stage.setTitle("SIGCAT - " + usuario.getNombre());

        } catch (IOException e) {
            lblError.setText("No se pudo cargar la siguiente pantalla: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Interfaz que deben implementar los controladores de menú que necesiten
     * recibir el usuario autenticado sin acoplar LoginController a cada uno.
     */
    public interface RecibeUsuario {
        void setUsuario(Usuario usuario);
    }
}
