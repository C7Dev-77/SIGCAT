package com.sigcat.controllers;

import com.sigcat.dao.UsuarioDAO;
import com.sigcat.models.Usuario;
import com.sigcat.utils.ViewNavigator;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Controlador de inicio de sesión con enrutamiento automático según rol
 * (PROPIETARIO -> menu-propietario.fxml | FUNCIONARIO -> menu-funcionario.fxml)
 * y acceso al registro autónomo de propietarios.
 */
public class LoginController {

    @FXML private TextField txtDocumento;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblErrorLogin;
    @FXML private Label lblMensajeExito;
    @FXML private Button btnIngresar;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    public void initialize() {
        if (lblErrorLogin != null) {
            lblErrorLogin.setText("");
        }
        if (lblMensajeExito != null) {
            lblMensajeExito.setText("");
            lblMensajeExito.setVisible(false);
            lblMensajeExito.setManaged(false);
        }
    }

    /**
     * Muestra feedback positivo al retornar de un registro exitoso,
     * autocompletando únicamente el documento por seguridad.
     */
    public void setRegistroExitoso(String documento) {
        if (txtDocumento != null && documento != null) {
            txtDocumento.setText(documento);
        }
        if (txtPassword != null) {
            txtPassword.clear(); // Seguridad: NO autocompletar la contraseña
        }
        if (lblMensajeExito != null) {
            lblMensajeExito.setText("✔ ¡Cuenta creada exitosamente! Ingrese su contraseña para continuar.");
            lblMensajeExito.setVisible(true);
            lblMensajeExito.setManaged(true);
        }
        if (lblErrorLogin != null) {
            lblErrorLogin.setText("");
        }
    }

    @FXML
    public void iniciarSesion(ActionEvent event) {
        String documento = txtDocumento != null ? txtDocumento.getText().trim() : "";
        String password = txtPassword != null ? txtPassword.getText() : "";

        if (documento.isEmpty() || password.isEmpty()) {
            mostrarError("Por favor ingrese su documento y contraseña.");
            return;
        }

        Usuario usuario = usuarioDAO.autenticar(documento, password);
        if (usuario == null) {
            mostrarError("Credenciales inválidas. Verifique su documento o contraseña.");
            return;
        }

        redirigirSegunRol(usuario, event);
    }

    @FXML
    public void onIngresar(ActionEvent event) {
        iniciarSesion(event);
    }

    private void redirigirSegunRol(Usuario usuario, ActionEvent event) {
        try {
            Stage stage = ViewNavigator.obtenerStage(event);
            if ("FUNCIONARIO".equalsIgnoreCase(usuario.getRol())) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-funcionario.fxml"));
                Parent root = loader.load();
                Object controller = loader.getController();
                if (controller instanceof RecibeUsuario recibeUsuario) {
                    recibeUsuario.setUsuario(usuario);
                }
                ViewNavigator.cambiarVista(stage, root, "SIGCAT — Consola de Funcionario Catastral (" + usuario.getNombre() + ")");
            } else {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-propietario.fxml"));
                Parent root = loader.load();
                MenuPropietarioController controller = loader.getController();
                if (controller != null) {
                    controller.inicializarSesionPropietario(usuario);
                }
                ViewNavigator.cambiarVista(stage, root, "SIGCAT — Portal del Propietario (" + usuario.getNombre() + ")");
            }
        } catch (IOException e) {
            mostrarError("Error al abrir el panel principal: " + e.getMessage());
        }
    }

    @FXML
    public void irARegistro(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/registro.fxml"));
            Parent root = loader.load();
            Stage stage = ViewNavigator.obtenerStage(event);
            ViewNavigator.cambiarVista(stage, root, "SIGCAT — Registro de Nuevo Propietario");
        } catch (IOException e) {
            mostrarError("No se pudo abrir el formulario de registro: " + e.getMessage());
        }
    }

    private void mostrarError(String mensaje) {
        if (lblErrorLogin != null) {
            lblErrorLogin.setText(mensaje);
            lblErrorLogin.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
        }
    }

    public interface RecibeUsuario {
        void setUsuario(Usuario usuario);
    }
}
