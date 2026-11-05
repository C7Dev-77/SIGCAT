package com.sigcat.controllers;

import com.sigcat.dao.UsuarioDAO;
import com.sigcat.models.Usuario;
import com.sigcat.utils.PasswordToggle;
import com.sigcat.utils.ViewNavigator;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Controlador de inicio de sesión con enrutamiento automático según rol:
 * - ADMIN -> menu-admin.fxml (Panel de Administración del Sistema)
 * - FUNCIONARIO -> menu-funcionario.fxml (Consola de Funcionario de la Alcaldía)
 * - PROPIETARIO -> menu-propietario.fxml (Portal del Propietario Ciudadano)
 */
public class LoginController {

    @FXML private TextField txtDocumento;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblErrorLogin;
    @FXML private Label lblMensajeExito;
    @FXML private Button btnIngresar;
    @FXML private Button btnVerPassword;
    @FXML private Label lblBloqMayus;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    public void initialize() {
        if (lblErrorLogin != null) {
            lblErrorLogin.setText("");
            // El mensaje de error solo ocupa espacio cuando hay algo que mostrar
            // (antes dejaba un hueco vacío entre la contraseña y el botón).
            lblErrorLogin.managedProperty().bind(lblErrorLogin.textProperty().isNotEmpty());
            lblErrorLogin.visibleProperty().bind(lblErrorLogin.textProperty().isNotEmpty());
        }
        if (lblMensajeExito != null) {
            lblMensajeExito.setText("");
            lblMensajeExito.setVisible(false);
            lblMensajeExito.setManaged(false);
        }

        PasswordToggle.vincular(txtPassword, btnVerPassword);

        // Aviso de Bloq Mayús mientras se escribe la contraseña.
        if (txtPassword != null) {
            txtPassword.setOnKeyReleased(e -> actualizarAvisoBloqMayus());
            txtPassword.focusedProperty().addListener((obs, antes, ahora) -> actualizarAvisoBloqMayus());
        }

        // Al escribir de nuevo, se borra el error anterior.
        if (txtDocumento != null) {
            txtDocumento.textProperty().addListener((obs, a, b) -> limpiarError());
        }
        if (txtPassword != null) {
            txtPassword.textProperty().addListener((obs, a, b) -> limpiarError());
        }
    }

    private void actualizarAvisoBloqMayus() {
        if (lblBloqMayus == null) return;
        boolean activo = false;
        try {
            activo = Platform.isKeyLocked(KeyCode.CAPS).orElse(false);
        } catch (UnsupportedOperationException | IllegalStateException ignored) {
            // Algunas plataformas no informan el estado de Bloq Mayús.
        }
        lblBloqMayus.setVisible(activo);
        lblBloqMayus.setManaged(activo);
    }

    private void limpiarError() {
        if (lblErrorLogin != null && !lblErrorLogin.getText().isEmpty()) {
            lblErrorLogin.setText("");
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

        // La verificación BCrypt tarda unos cientos de milisegundos: se hace en
        // segundo plano para que la ventana no se congele y el botón muestre
        // "Ingresando…" (y no se pueda pulsar dos veces).
        bloquearFormulario(true);
        Task<Usuario> tarea = new Task<>() {
            @Override
            protected Usuario call() {
                return usuarioDAO.autenticar(documento, password);
            }
        };
        tarea.setOnSucceeded(e -> {
            Usuario usuario = tarea.getValue();
            if (usuario == null) {
                bloquearFormulario(false);
                mostrarError("Documento o contraseña incorrectos.");
                if (txtPassword != null) {
                    txtPassword.clear();
                    txtPassword.requestFocus();
                }
                return;
            }
            redirigirSegunRol(usuario, event);
        });
        tarea.setOnFailed(e -> {
            bloquearFormulario(false);
            mostrarError("No se pudo verificar el acceso. Intente de nuevo.");
            if (tarea.getException() != null) {
                tarea.getException().printStackTrace();
            }
        });
        Thread hilo = new Thread(tarea, "sigcat-login");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void bloquearFormulario(boolean ocupado) {
        if (btnIngresar != null) {
            btnIngresar.setDisable(ocupado);
            btnIngresar.setText(ocupado ? "Ingresando…" : "Ingresar");
        }
        if (txtDocumento != null) txtDocumento.setDisable(ocupado);
        if (txtPassword != null) txtPassword.setDisable(ocupado);
        if (btnVerPassword != null) btnVerPassword.setDisable(ocupado);
    }

    @FXML
    public void onIngresar(ActionEvent event) {
        iniciarSesion(event);
    }

    private void redirigirSegunRol(Usuario usuario, ActionEvent event) {
        try {
            Stage stage = ViewNavigator.obtenerStage(event);
            if ("ADMIN".equalsIgnoreCase(usuario.getRol())) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-admin.fxml"));
                Parent root = loader.load();
                Object controller = loader.getController();
                if (controller instanceof RecibeUsuario recibeUsuario) {
                    recibeUsuario.setUsuario(usuario);
                }
                ViewNavigator.cambiarVista(stage, root, "SIGCAT — Panel de Administración del Sistema (" + usuario.getNombre() + ")");
            } else if ("FUNCIONARIO".equalsIgnoreCase(usuario.getRol())) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/menu-funcionario.fxml"));
                Parent root = loader.load();
                Object controller = loader.getController();
                if (controller instanceof RecibeUsuario recibeUsuario) {
                    recibeUsuario.setUsuario(usuario);
                }
                ViewNavigator.cambiarVista(stage, root, "SIGCAT — Consola de Funcionario Catastral (Alcaldía de Sincelejo)");
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
            bloquearFormulario(false);
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
        }
    }

    public interface RecibeUsuario {
        void setUsuario(Usuario usuario);
    }
}
