package com.sigcat.controllers;

import com.sigcat.dao.UsuarioDAO;
import com.sigcat.utils.PasswordToggle;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Controlador para el registro autónomo de Propietarios Catastrales.
 * Aplica validaciones de integridad, unicidad de documento y fortaleza de contraseña.
 */
public class RegistroController {

    @FXML private TextField txtDocumentoRegistro;
    @FXML private TextField txtNombreRegistro;
    @FXML private PasswordField txtPasswordRegistro;
    @FXML private PasswordField txtConfirmarPassword;
    @FXML private Label lblMensajeRegistro;
    @FXML private Button btnRegistrar;
    @FXML private Button btnVerPassword;
    @FXML private Button btnVerConfirmar;
    @FXML private Label lblBloqMayus;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    public void initialize() {
        if (lblMensajeRegistro != null) {
            lblMensajeRegistro.setText("");
            // Solo ocupa espacio cuando hay un mensaje que mostrar.
            lblMensajeRegistro.managedProperty().bind(lblMensajeRegistro.textProperty().isNotEmpty());
            lblMensajeRegistro.visibleProperty().bind(lblMensajeRegistro.textProperty().isNotEmpty());
        }

        PasswordToggle.vincular(txtPasswordRegistro, btnVerPassword);
        PasswordToggle.vincular(txtConfirmarPassword, btnVerConfirmar);

        for (PasswordField campo : new PasswordField[] {txtPasswordRegistro, txtConfirmarPassword}) {
            if (campo != null) {
                campo.setOnKeyReleased(e -> actualizarAvisoBloqMayus());
                campo.focusedProperty().addListener((obs, antes, ahora) -> actualizarAvisoBloqMayus());
            }
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

    @FXML
    public void registrarNuevoPropietario(ActionEvent event) {
        String documento = txtDocumentoRegistro != null ? txtDocumentoRegistro.getText().trim() : "";
        String nombre = txtNombreRegistro != null ? txtNombreRegistro.getText().trim() : "";
        String password = txtPasswordRegistro != null ? txtPasswordRegistro.getText() : "";
        String confirmar = txtConfirmarPassword != null ? txtConfirmarPassword.getText() : "";

        // 1. Validar campos obligatorios
        if (documento.isEmpty() || nombre.isEmpty() || password.isEmpty() || confirmar.isEmpty()) {
            mostrarMensaje("Todos los campos marcados con (*) son obligatorios.", "#ef4444");
            return;
        }

        // 2. Validar formato de documento (mínimo 5 caracteres alfanuméricos/numéricos)
        if (!documento.matches("^[0-9A-Za-z-]{5,20}$")) {
            mostrarMensaje("El documento debe contener entre 5 y 20 caracteres válidos (sin espacios).", "#ef4444");
            return;
        }

        // 3. Validar longitud y robustez mínima de contraseña
        if (password.length() < 6) {
            mostrarMensaje("La contraseña debe tener al menos 6 caracteres de longitud.", "#ef4444");
            return;
        }

        // 4. Validar coincidencia de contraseñas
        if (!password.equals(confirmar)) {
            mostrarMensaje("Las contraseñas ingresadas no coinciden.", "#ef4444");
            return;
        }

        mostrarMensaje("", "#ef4444");

        // 5 y 6. Unicidad del documento y guardado con BCrypt (costo 12): tarda un
        // momento, así que se hace en segundo plano con el botón en "Creando cuenta…".
        bloquearFormulario(true);
        Task<Integer> tarea = new Task<>() {
            @Override
            protected Integer call() {
                if (usuarioDAO.existeDocumento(documento)) {
                    return -2; // documento ya registrado
                }
                return usuarioDAO.registrarPropietario(documento, nombre, password);
            }
        };
        tarea.setOnSucceeded(e -> {
            int resultado = tarea.getValue();
            if (resultado > 0) {
                navegarALoginConExito(event, documento);
                return;
            }
            bloquearFormulario(false);
            if (resultado == -2) {
                mostrarMensaje("Ya existe un propietario registrado con el documento " + documento + ".", "#ef4444");
            } else {
                mostrarMensaje("Ocurrió un error al registrar el usuario en la base de datos.", "#ef4444");
            }
        });
        tarea.setOnFailed(e -> {
            bloquearFormulario(false);
            mostrarMensaje("No se pudo completar el registro. Intente de nuevo.", "#ef4444");
            if (tarea.getException() != null) {
                tarea.getException().printStackTrace();
            }
        });
        Thread hilo = new Thread(tarea, "sigcat-registro");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void bloquearFormulario(boolean ocupado) {
        if (btnRegistrar != null) {
            btnRegistrar.setDisable(ocupado);
            btnRegistrar.setText(ocupado ? "Creando cuenta…" : "Crear cuenta");
        }
        for (javafx.scene.control.TextInputControl c : new javafx.scene.control.TextInputControl[] {
                txtDocumentoRegistro, txtNombreRegistro, txtPasswordRegistro, txtConfirmarPassword}) {
            if (c != null) c.setDisable(ocupado);
        }
    }

    @FXML
    public void volverALogin(ActionEvent event) {
        navegarALogin(event);
    }

    private void navegarALoginConExito(ActionEvent event, String documento) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            LoginController controller = loader.getController();
            if (controller != null) {
                controller.setRegistroExitoso(documento);
            }
            Stage stage = com.sigcat.utils.ViewNavigator.obtenerStage(event);
            com.sigcat.utils.ViewNavigator.cambiarVista(stage, root, "SIGCAT — Iniciar Sesión");
        } catch (IOException e) {
            bloquearFormulario(false);
            mostrarMensaje("Error al redirigir al inicio de sesión: " + e.getMessage(), "#ef4444");
        }
    }

    private void navegarALogin(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Stage stage = com.sigcat.utils.ViewNavigator.obtenerStage(event);
            com.sigcat.utils.ViewNavigator.cambiarVista(stage, root, "SIGCAT — Iniciar Sesión");
        } catch (IOException e) {
            mostrarMensaje("No se pudo cargar la pantalla de Login: " + e.getMessage(), "#ef4444");
        }
    }

    private void mostrarMensaje(String texto, String colorHex) {
        if (lblMensajeRegistro != null) {
            lblMensajeRegistro.setText(texto);
            // Rojo claro para que se lea sobre la tarjeta oscura.
            String color = "#ef4444".equalsIgnoreCase(colorHex) ? "#f87171" : colorHex;
            lblMensajeRegistro.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        }
    }
}
