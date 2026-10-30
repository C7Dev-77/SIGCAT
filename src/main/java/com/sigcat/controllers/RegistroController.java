package com.sigcat.controllers;

import com.sigcat.dao.UsuarioDAO;
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

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    public void initialize() {
        if (lblMensajeRegistro != null) {
            lblMensajeRegistro.setText("");
        }
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

        // 5. Validar unicidad del documento en la base de datos
        if (usuarioDAO.existeDocumento(documento)) {
            mostrarMensaje("Ya existe un propietario registrado con el documento: " + documento, "#ef4444");
            return;
        }

        // 6. Persistir nuevo propietario con BCrypt cost factor 12
        int nuevoId = usuarioDAO.registrarPropietario(documento, nombre, password);
        if (nuevoId > 0) {
            navegarALoginConExito(event, documento);
        } else {
            mostrarMensaje("Ocurrió un error al registrar el usuario en la base de datos.", "#ef4444");
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
            lblMensajeRegistro.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: bold;");
        }
    }
}
