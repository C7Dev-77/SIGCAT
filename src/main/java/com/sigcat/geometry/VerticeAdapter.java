package com.sigcat.geometry;

import java.util.ArrayList;
import java.util.List;

import com.sigcat.models.Vertice;

import javafx.geometry.Point2D;

/**
 * Adaptador entre los puntos capturados en el Canvas (JavaFX) y el modelo
 * Vertice usado por la lógica geométrica oficial (Shoelace / JTS).
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class VerticeAdapter {

    /**
     * Convierte los puntos dibujados en el Canvas (en el orden en que se
     * hizo clic) a la lista de Vertice que usan ShoelaceCalculator y
     * SolapamientoValidator. idPredio se deja en 0 porque aún no existe
     * (se asigna al guardar en la base de datos).
     */
    public static List<Vertice> desdeCanvas(List<Point2D> puntosCanvas) {
        List<Vertice> vertices = new ArrayList<>();
        for (int i = 0; i < puntosCanvas.size(); i++) {
            Point2D p = puntosCanvas.get(i);
            vertices.add(new Vertice(0, i + 1, p.getX(), p.getY()));
        }
        return vertices;
    }
}