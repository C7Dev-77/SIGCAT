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
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controlador del panel del Funcionario catastral (Fase 4).
 * Muestra las solicitudes con filtro por estado, un resumen con contadores
 * y permite aprobar o rechazar las que están pendientes.
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class MenuFuncionarioController implements LoginController.RecibeUsuario, Initializable {

    private static final String ESTILO_NEUTRO = "-fx-text-fill: #94a3b8;";
    private static final String ESTILO_ERROR = "-fx-text-fill: #ef4444;";
    private static final String ESTILO_OK = "-fx-text-fill: #4caf50;";

    @FXML private Label lblUsuario;
    @FXML private Label lblIniciales;
    @FXML private Label lblMensaje;
    @FXML private Label lblPendientes;
    @FXML private Label lblAprobados;
    @FXML private Label lblRechazados;
    @FXML private ComboBox<String> cmbFiltro;
    @FXML private TableView<SolicitudRow> tablaSolicitudes;
    @FXML private TableColumn<SolicitudRow, String> colEstado;

    private final PredioDAO predioDAO = new PredioDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    private Usuario funcionario;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colEstado.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean empty) {
                super.updateItem(estado, empty);
                if (empty || estado == null) {
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(estado);
                badge.getStyleClass().add("badge-estado");
                switch (estado) {
                    case "APROBADO" -> badge.getStyleClass().add("badge-aprobado");
                    case "RECHAZADO" -> badge.getStyleClass().add("badge-rechazado");
                    default -> badge.getStyleClass().add("badge-pendiente");
                }
                setGraphic(badge);
            }
        });

        cmbFiltro.setItems(FXCollections.observableArrayList(
            "Pendientes", "Todas", "Aprobadas", "Rechazadas"));
        cmbFiltro.setValue("Todas");
        // Se pone después del setValue para que no recargue antes de tener funcionario
        cmbFiltro.setOnAction(e -> cargarSolicitudes());
    }

    @Override
    public void setUsuario(Usuario usuario) {
        this.funcionario = usuario;
        lblUsuario.setText(usuario.getNombre());
        lblIniciales.setText(iniciales(usuario.getNombre()));
        cargarSolicitudes();
    }

    private String iniciales(String nombre) {
        StringBuilder sb = new StringBuilder();
        for (String parte : nombre.trim().split("\\s+")) {
            if (parte.isEmpty() || parte.startsWith("(")) {
                continue;
            }
            sb.append(Character.toUpperCase(parte.charAt(0)));
            if (sb.length() == 2) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Consulta todos los predios, actualiza los contadores y arma la tabla
     * según el filtro elegido, resolviendo el nombre de cada propietario.
     */
    private void cargarSolicitudes() {
        try {
            List<Predio> todos = predioDAO.listarTodos();

            lblPendientes.setText(String.valueOf(
                todos.stream().filter(p -> p.getEstado() == Predio.Estado.PENDIENTE).count()));
            lblAprobados.setText(String.valueOf(
                todos.stream().filter(p -> p.getEstado() == Predio.Estado.APROBADO).count()));
            lblRechazados.setText(String.valueOf(
                todos.stream().filter(p -> p.getEstado() == Predio.Estado.RECHAZADO).count()));

            String filtro = cmbFiltro.getValue();
            Map<Integer, String> nombres = new HashMap<>();
            ObservableList<SolicitudRow> filas = FXCollections.observableArrayList();

            for (Predio predio : todos) {
                if (!coincideConFiltro(predio, filtro)) {
                    continue;
                }
                filas.add(new SolicitudRow(predio, nombrePropietario(predio.getIdPropietario(), nombres)));
            }

            tablaSolicitudes.setItems(filas);

            String etiqueta = "Pendientes".equals(filtro) ? "pendiente(s)" : "en la lista";
            mostrarMensaje(filas.isEmpty()
                ? "No hay solicitudes para mostrar."
                : filas.size() + " solicitud(es) " + etiqueta + ".", ESTILO_NEUTRO);

        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensaje("Error al cargar las solicitudes: " + e.getMessage(), ESTILO_ERROR);
        }
    }

    private boolean coincideConFiltro(Predio predio, String filtro) {
        if (filtro == null) {
            return true;
        }
        return switch (filtro) {
            case "Pendientes" -> predio.getEstado() == Predio.Estado.PENDIENTE;
            case "Aprobadas" -> predio.getEstado() == Predio.Estado.APROBADO;
            case "Rechazadas" -> predio.getEstado() == Predio.Estado.RECHAZADO;
            default -> true;
        };
    }

    private String nombrePropietario(int id, Map<Integer, String> cache) throws SQLException {
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        String nombre = usuarioDAO.buscarPorId(id).map(Usuario::getNombre).orElse("Desconocido");
        cache.put(id, nombre);
        return nombre;
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
            mostrarMensaje("Selecciona una solicitud de la tabla primero.", ESTILO_ERROR);
            return;
        }

        if (!"PENDIENTE".equals(seleccionada.getEstado())) {
            mostrarMensaje("Esa solicitud ya fue resuelta. Solo se pueden decidir las pendientes.", ESTILO_ERROR);
            return;
        }

        try {
            predioDAO.actualizarEstado(seleccionada.getId(), nuevoEstado, funcionario.getId());
            cargarSolicitudes();
            String resultado = nuevoEstado == Predio.Estado.APROBADO ? "aprobada" : "rechazada";
            mostrarMensaje("Solicitud #" + seleccionada.getId() + " " + resultado + ".", ESTILO_OK);
        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensaje("No se pudo actualizar la solicitud: " + e.getMessage(), ESTILO_ERROR);
        }
    }

    private void mostrarMensaje(String texto, String estilo) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle(estilo);
    }

    /**
     * Cierra la sesión y retorna a la pantalla de login.
     */
    @FXML
    private void handleCerrarSesion(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Parent root = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(
            getClass().getResource("/styles/main.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.setTitle("SIGCAT - Sistema Catastral");
    }
}