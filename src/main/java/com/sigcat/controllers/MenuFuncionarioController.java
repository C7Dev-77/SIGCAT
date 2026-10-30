package com.sigcat.controllers;

import com.sigcat.dao.PredioDAO;
import com.sigcat.dao.UsuarioDAO;
import com.sigcat.dao.VerticeDAO;
import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.models.Vertice;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controlador del panel del Funcionario catastral (Fase 4).
 * Lista las solicitudes con filtro y contadores, muestra el detalle del
 * expediente elegido (plano del predio y validación de linderos) y permite
 * aprobarlo o rechazarlo. No deja aprobar un predio que se cruza con otro
 * ya aprobado.
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class MenuFuncionarioController implements LoginController.RecibeUsuario, Initializable {

    private static final String ESTILO_NEUTRO = "-fx-text-fill: #94a3b8;";
    private static final String ESTILO_ERROR = "-fx-text-fill: #ef4444;";
    private static final String ESTILO_OK = "-fx-text-fill: #4caf50;";

    private static final String CLASE_OK = "pill-ok";
    private static final String CLASE_ERROR = "pill-error";
    private static final String CLASE_NEUTRO = "pill-neutro";

    @FXML private Label lblUsuario;
    @FXML private Label lblIniciales;
    @FXML private Label lblMensaje;
    @FXML private Label lblPendientes;
    @FXML private Label lblAprobados;
    @FXML private Label lblRechazados;
    @FXML private Circle puntoAlerta;
    @FXML private ComboBox<String> cmbFiltro;
    @FXML private TableView<SolicitudRow> tablaSolicitudes;
    @FXML private TableColumn<SolicitudRow, String> colEstado;

    @FXML private Label lblDetalleTitulo;
    @FXML private Label lblDetallePropietario;
    @FXML private Label lblDetalleArea;
    @FXML private Label lblLinderos;
    @FXML private Canvas canvasMapa;
    @FXML private Button btnAprobar;
    @FXML private Button btnRechazar;

    private final PredioDAO predioDAO = new PredioDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final VerticeDAO verticeDAO = new VerticeDAO();

    private Usuario funcionario;

    // Predios aprobados (id -> polígono), usados para validar linderos y dibujar el contexto
    private final Map<Integer, Polygon> poligonosAprobados = new LinkedHashMap<>();

    // Estado del expediente seleccionado
    private Polygon poligonoSeleccion = null;
    private int idSeleccion = -1;
    private final List<Integer> idsEnConflicto = new ArrayList<>();

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
        cmbFiltro.setValue("Pendientes");
        // Se pone después del setValue para que no recargue antes de tener funcionario
        cmbFiltro.setOnAction(e -> cargarSolicitudes());

        tablaSolicitudes.getSelectionModel().selectedItemProperty()
            .addListener((obs, anterior, actual) -> mostrarDetalle(actual));

        mostrarDetalle(null);
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

    // ─── Carga de solicitudes ──────────────────────────────────────────────

    private void cargarSolicitudes() {
        try {
            List<Predio> todos = predioDAO.listarTodos();
            cargarPoligonosAprobados();

            long pendientes = todos.stream().filter(p -> p.getEstado() == Predio.Estado.PENDIENTE).count();
            long aprobadas = todos.stream().filter(p -> p.getEstado() == Predio.Estado.APROBADO).count();
            long rechazadas = todos.stream().filter(p -> p.getEstado() == Predio.Estado.RECHAZADO).count();

            lblPendientes.setText(String.valueOf(pendientes));
            lblAprobados.setText(String.valueOf(aprobadas));
            lblRechazados.setText(String.valueOf(rechazadas));
            puntoAlerta.setVisible(pendientes > 0);

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

    private void cargarPoligonosAprobados() throws SQLException {
        poligonosAprobados.clear();
        for (Predio predio : predioDAO.listarAprobadosConVertices()) {
            if (predio.getVertices() != null && predio.getVertices().size() >= 3) {
                poligonosAprobados.put(predio.getId(),
                    SolapamientoValidator.construirPoligono(predio.getVertices()));
            }
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

    // ─── Detalle del expediente ────────────────────────────────────────────

    private void mostrarDetalle(SolicitudRow fila) {
        idsEnConflicto.clear();
        poligonoSeleccion = null;
        idSeleccion = -1;
        btnAprobar.setDisable(true);

        if (fila == null) {
            lblDetalleTitulo.setText("Ningún expediente seleccionado");
            lblDetallePropietario.setText("Elige una solicitud de la tabla para ver el detalle.");
            lblDetalleArea.setText("-");
            mostrarLinderos("Sin datos", CLASE_NEUTRO);
            btnRechazar.setDisable(true);
            dibujarMapa();
            return;
        }

        boolean pendiente = "PENDIENTE".equals(fila.getEstado());
        idSeleccion = fila.getId();
        lblDetalleTitulo.setText("Expediente #" + fila.getId());
        lblDetallePropietario.setText(fila.getPropietario() + "  ·  " + fila.getFecha());
        lblDetalleArea.setText(String.format("%.2f px²", fila.getArea()));
        btnRechazar.setDisable(!pendiente);

        try {
            List<Vertice> vertices = verticeDAO.obtenerPorPredio(fila.getId());
            if (vertices.size() < 3) {
                mostrarLinderos("Datos incompletos", CLASE_ERROR);
                dibujarMapa();
                return;
            }
            poligonoSeleccion = SolapamientoValidator.construirPoligono(vertices);
        } catch (SQLException e) {
            e.printStackTrace();
            mostrarLinderos("Error al leer", CLASE_ERROR);
            mostrarMensaje("No se pudo leer el predio: " + e.getMessage(), ESTILO_ERROR);
            dibujarMapa();
            return;
        }

        // Se compara contra los aprobados, sin contar el propio expediente
        List<Integer> ids = new ArrayList<>();
        List<Polygon> otros = new ArrayList<>();
        for (Map.Entry<Integer, Polygon> entrada : poligonosAprobados.entrySet()) {
            if (entrada.getKey() != fila.getId()) {
                ids.add(entrada.getKey());
                otros.add(entrada.getValue());
            }
        }
        for (int indice : SolapamientoValidator.indicesEnConflicto(poligonoSeleccion, otros)) {
            idsEnConflicto.add(ids.get(indice));
        }

        if (idsEnConflicto.isEmpty()) {
            mostrarLinderos("Sin conflicto", CLASE_OK);
        } else {
            mostrarLinderos("Conflicto con " + textoIds(idsEnConflicto), CLASE_ERROR);
        }

        btnAprobar.setDisable(!pendiente || !idsEnConflicto.isEmpty());
        dibujarMapa();
    }

    private String textoIds(List<Integer> ids) {
        StringBuilder sb = new StringBuilder(ids.size() == 1 ? "predio " : "predios ");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("#").append(ids.get(i));
        }
        return sb.toString();
    }

    private void mostrarLinderos(String texto, String clase) {
        lblLinderos.setText(texto);
        lblLinderos.getStyleClass().removeAll(CLASE_OK, CLASE_ERROR, CLASE_NEUTRO);
        lblLinderos.getStyleClass().add(clase);
    }

    // ─── Plano del predio (mapa nativo en Canvas) ──────────────────────────

    private void dibujarMapa() {
        GraphicsContext gc = canvasMapa.getGraphicsContext2D();
        double ancho = canvasMapa.getWidth();
        double alto = canvasMapa.getHeight();

        // Fondo claro con cuadrícula
        gc.setFill(Color.web("#eef2f7"));
        gc.fillRect(0, 0, ancho, alto);
        gc.setStroke(Color.web("#d7dee9"));
        gc.setLineWidth(1.0);
        for (double x = 0; x <= ancho; x += 20) {
            gc.strokeLine(x, 0, x, alto);
        }
        for (double y = 0; y <= alto; y += 20) {
            gc.strokeLine(0, y, ancho, y);
        }

        if (poligonoSeleccion == null) {
            gc.setFill(Color.web("#8a97ab"));
            gc.setFont(Font.font(13));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("Selecciona un expediente", ancho / 2, alto / 2);
            gc.setTextAlign(TextAlignment.LEFT);
            return;
        }

        // Encuadre: el predio elegido y los que entran en conflicto con él
        Envelope vista = new Envelope(poligonoSeleccion.getEnvelopeInternal());
        for (Integer id : idsEnConflicto) {
            vista.expandToInclude(poligonosAprobados.get(id).getEnvelopeInternal());
        }

        double w = Math.max(vista.getWidth(), 1.0);
        double h = Math.max(vista.getHeight(), 1.0);
        double margen = 30.0;
        double esc = Math.min((ancho - 2 * margen) / w, (alto - 2 * margen) / h);
        double offX = (ancho - w * esc) / 2 - vista.getMinX() * esc;
        double offY = (alto - h * esc) / 2 - vista.getMinY() * esc;

        Envelope visible = new Envelope(-offX / esc, (ancho - offX) / esc, -offY / esc, (alto - offY) / esc);

        gc.save();
        gc.translate(offX, offY);
        gc.scale(esc, esc);

        // Predios aprobados que caen dentro de la vista
        for (Map.Entry<Integer, Polygon> entrada : poligonosAprobados.entrySet()) {
            if (entrada.getKey() == idSeleccion
                    || !entrada.getValue().getEnvelopeInternal().intersects(visible)) {
                continue;
            }
            if (idsEnConflicto.contains(entrada.getKey())) {
                trazarPoligono(gc, entrada.getValue(), Color.web("#ef4444", 0.25), Color.web("#ef4444"), 2.0 / esc);
            } else {
                trazarPoligono(gc, entrada.getValue(), Color.web("#94a3b8", 0.35), Color.web("#64748b"), 1.5 / esc);
            }
        }

        // Expediente elegido, en naranja
        trazarPoligono(gc, poligonoSeleccion, Color.web("#f97316", 0.25), Color.web("#f97316"), 2.5 / esc);

        Coordinate[] coords = poligonoSeleccion.getExteriorRing().getCoordinates();
        double radio = 3.5 / esc;
        gc.setFill(Color.web("#f97316"));
        for (int i = 0; i < coords.length - 1; i++) {
            gc.fillOval(coords[i].x - radio, coords[i].y - radio, radio * 2, radio * 2);
        }

        gc.restore();
    }

    private void trazarPoligono(GraphicsContext gc, Polygon poligono, Color relleno, Color borde, double grosor) {
        Coordinate[] coords = poligono.getExteriorRing().getCoordinates();
        double[] xs = new double[coords.length];
        double[] ys = new double[coords.length];
        for (int i = 0; i < coords.length; i++) {
            xs[i] = coords[i].x;
            ys[i] = coords[i].y;
        }
        gc.setFill(relleno);
        gc.fillPolygon(xs, ys, xs.length);
        gc.setStroke(borde);
        gc.setLineWidth(grosor);
        gc.strokePolygon(xs, ys, xs.length);
    }

    // ─── Acciones ──────────────────────────────────────────────────────────

    @FXML
    private void handleCampana() {
        if ("Pendientes".equals(cmbFiltro.getValue())) {
            cargarSolicitudes();
        } else {
            cmbFiltro.setValue("Pendientes");
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
            mostrarMensaje("Selecciona una solicitud de la tabla primero.", ESTILO_ERROR);
            return;
        }

        if (!"PENDIENTE".equals(seleccionada.getEstado())) {
            mostrarMensaje("Esa solicitud ya fue resuelta. Solo se pueden decidir las pendientes.", ESTILO_ERROR);
            return;
        }

        if (nuevoEstado == Predio.Estado.APROBADO && !idsEnConflicto.isEmpty()) {
            mostrarMensaje("No se puede aprobar: el predio se cruza con otro ya aprobado. Puedes rechazarlo.", ESTILO_ERROR);
            return;
        }

        int id = seleccionada.getId();
        try {
            predioDAO.actualizarEstado(id, nuevoEstado, funcionario.getId());
            cargarSolicitudes();
            String resultado = nuevoEstado == Predio.Estado.APROBADO ? "aprobada" : "rechazada";
            mostrarMensaje("Solicitud #" + id + " " + resultado + ".", ESTILO_OK);
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