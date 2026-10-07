package com.sigcat.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.locationtech.jts.geom.Polygon;

import com.sigcat.dao.PredioDAO;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Controlador del menú del Propietario (Fase 1 & 2).
 *
 * Responsable: Leider Barreto (UI/UX)
 *
 * Esta pantalla recibe el usuario autenticado desde LoginController
 * y permite navegar al Canvas de dibujo de predios.
 */
public class MenuPropietarioController implements LoginController.RecibeUsuario {

    @FXML private Label lblBienvenida;

    private Usuario usuario;

    @Override
    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        if (lblBienvenida != null) {
            lblBienvenida.setText("Bienvenido, " + usuario.getNombre());
        }
    }

    /**
     * Navega a la pantalla de dibujo de predios e inyecta el propietario.
     */
    @FXML
    private void irADibujar(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dibujo.fxml"));
        Parent root = loader.load();

        DibujoController controller = loader.getController();
        controller.setPropietario(usuario);

        try {
            PredioDAO predioDAO = new PredioDAO();
            List<Predio> aprobados = predioDAO.listarAprobadosConVertices();
            List<Polygon> poligonosAprobados = aprobados.stream()
                .map(p -> SolapamientoValidator.construirPoligono(p.getVertices()))
                .toList();
            controller.setPrediosAprobados(poligonosAprobados);
        } catch (SQLException e) {
            e.printStackTrace();
            // El Canvas se muestra igual, pero sin predios previos cargados para comparar.
        }

        Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root, 1024, 768);
        scene.getStylesheets().add(
            getClass().getResource("/styles/main.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.setTitle("SIGCAT - Registrar nuevo predio");
        stage.setResizable(false);
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
