package com.sigcat.controllers;

import com.sigcat.models.Usuario;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Controlador placeholder del menú del Funcionario (Fase 1 / stub para Fase 4).
 *
 * Responsable: Leider Barreto (UI/UX) — stub para navegación de Login.
 *
 * NOTA: El panel real de aprobación (tabla de PENDIENTES, botones Aprobar/Rechazar)
 * es responsabilidad de Andrés en la Fase 4. Este controlador solo evita que el
 * ruteo por rol del Login falle mientras tanto.
 */
public class MenuFuncionarioController implements LoginController.RecibeUsuario {

    @FXML private Label lblBienvenida;

    @Override
    public void setUsuario(Usuario usuario) {
        if (lblBienvenida != null) {
            lblBienvenida.setText("Bienvenido, " + usuario.getNombre());
        }
    }

    /**
     * Cierra la sesión y retorna a la pantalla de login.
     */
    @FXML
    private void handleCerrarSesion(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Parent root = loader.load();

        Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(
            getClass().getResource("/styles/main.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.setTitle("SIGCAT - Sistema Catastral");
    }
}
