package com.sigcat.logic;

import javafx.geometry.Point2D;

import java.util.List;

/**
 * Implementa la fórmula de Gauss (Shoelace) para el área de un polígono simple.
 *
 * Implementado por: Leider Barreto (UI/UX) — versión temporal de prueba.
 * NOTA PARA EL EQUIPO: esta clase es formalmente responsabilidad de Andrés
 * (Fase 2). Se incluye aquí para poder probar el Canvas de forma independiente
 * mientras él entrega la versión oficial. Cuando la tenga, se sustituye esta
 * clase por la suya (misma firma de método, para que DibujoController no
 * necesite cambios).
 */
public class ShoelaceCalculator {

    /**
     * Calcula el área de un polígono usando el algoritmo de Gauss (Shoelace).
     *
     * @param vertices vértices del polígono en orden (sentido horario o antihorario,
     *                 no es necesario cerrarlo repitiendo el primer punto).
     * @return área en las mismas unidades al cuadrado que las coordenadas del Canvas.
     */
    public static double calcularArea(List<Point2D> vertices) {
        int n = vertices.size();
        if (n < 3) {
            return 0.0;
        }

        double sumatoria = 0.0;
        for (int i = 0; i < n; i++) {
            Point2D actual = vertices.get(i);
            Point2D siguiente = vertices.get((i + 1) % n);
            sumatoria += (actual.getX() * siguiente.getY()) - (siguiente.getX() * actual.getY());
        }

        return Math.abs(sumatoria) / 2.0;
    }
}
