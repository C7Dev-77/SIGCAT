package com.sigcat.controllers;

import com.sigcat.dao.PredioDAO;
import com.sigcat.dao.UsuarioDAO;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Controlador del panel del Funcionario catastral (Fase 4).
 * Lista los predios PENDIENTES y permite aprobarlos o rechazarlos.
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class MenuFuncionarioController implements LoginController.RecibeUsuario {

    @FXML private Label lblBienvenida;
    @FXML private Label lblMensaje;
    @FXML private TableView<SolicitudRow> tablaSolicitudes;

    private final PredioDAO predioDAO = new PredioDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    private Usuario funcionario;

    @Override
    public void setUsuario(Usuario usuario) {
        this.funcionario = usuario;
        if (lblBienvenida != null) {
            lblBienvenida.setText("Bienvenido, " + usuario.getNombre());
        }
        cargarPendientes();
    }

    /**
     * Consulta los predios PENDIENTES y arma las filas de la tabla,
     * resolviendo el nombre del propietario para cada uno.
     */
    private void cargarPendientes() {
        try {
            List<Predio> pendientes = predioDAO.listarPendientes();
            ObservableList<SolicitudRow> filas = FXCollections.observableArrayList();

            for (Predio predio : pendientes) {
                String nombrePropietario = "Desconocido";
                Optional<Usuario> propietario = usuarioDAO.buscarPorId(predio.getIdPropietario());
                if (propietario.isPresent()) {
                    nombrePropietario = propietario.get().getNombre();
                }
                filas.add(new SolicitudRow(predio, nombrePropietario));
            }

            tablaSolicitudes.setItems(filas);
            lblMensaje.setText(filas.isEmpty()
                ? "No hay solicitudes pendientes."
                : filas.size() + " solicitud(es) pendiente(s).");
            lblMensaje.setStyle("-fx-text-fill: #a0a0c0;");

        } catch (SQLException e) {
            e.printStackTrace();
            lblMensaje.setText("Error al cargar las solicitudes: " + e.getMessage());
            lblMensaje.setStyle("-fx-text-fill: #e94560;");
        }
    }

    @FXML
    private void handleAprobar(ActionEvent event) {
        procesarDecision(Predio.Estado.APROBADO);
    }

    @FXML
    private void handleRechazar(ActionEvent event) {
        procesarDecision(Predio.Estado.RECHAZADO);
    }

    private void procesarDecision(Predio.Estado nuevoEstado) {
        SolicitudRow seleccionada = tablaSolicitudes.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            lblMensaje.setText("Selecciona una solicitud de la tabla primero.");
            lblMensaje.setStyle("-fx-text-fill: #e94560;");
            return;
        }

        try {
            predioDAO.actualizarEstado(seleccionada.getId(), nuevoEstado, funcionario.getId());
            lblMensaje.setText("Solicitud #" + seleccionada.getId() + " " + nuevoEstado.name().toLowerCase() + ".");
            lblMensaje.setStyle("-fx-text-fill: #4caf50;");
            cargarPendientes(); // refresca la tabla (la solicitud ya procesada desaparece de PENDIENTES)
        } catch (SQLException e) {
            e.printStackTrace();
            lblMensaje.setText("No se pudo actualizar la solicitud: " + e.getMessage());
            lblMensaje.setStyle("-fx-text-fill: #e94560;");
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