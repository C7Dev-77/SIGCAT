package com.sigcat.geometry;

import com.sigcat.models.Vertice;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.awt.geom.Line2D;
import java.util.Comparator;
import java.util.List;

/**
 * Motor de validación topológica para detectar invasión de linderos,
 * auto-intersecciones y solapamiento entre predios registrados.
 */
public class SolapamientoValidator {

    private static final double EPSILON = 1e-9;
    private static final GeometryFactory FACTORY = new GeometryFactory();

    /**
     * Verifica si dos polígonos catastrales se solapan (se cruzan sus aristas o uno contiene al otro).
     * Permite que compartan linderos (bordes colineales o vértices en común) sin marcar falso positivo,
     * o bloquea según la sensibilidad requerida.
     *
     * @param poligonoA Lista de vértices del nuevo predio.
     * @param poligonoB Lista de vértices de un predio existente.
     * @return true si existe solapamiento ilegal de áreas, false si son disjuntos.
     */
    public static boolean PoligonosSeSolapan(List<Vertice> poligonoA, List<Vertice> poligonoB) {
        if (poligonoA == null || poligonoB == null || poligonoA.size() < 3 || poligonoB.size() < 3) {
            return false;
        }

        // 1. Optimización rápida por Bounding Box (Caja Delimitadora)
        if (!boundingBoxesSeCruzan(poligonoA, poligonoB)) {
            return false;
        }

        int nA = poligonoA.size();
        int nB = poligonoB.size();

        // 2. Verificar intersección estricta entre todos los segmentos de A y B
        for (int i = 0; i < nA; i++) {
            Vertice a1 = poligonoA.get(i);
            Vertice a2 = poligonoA.get((i + 1) % nA);

            for (int j = 0; j < nB; j++) {
                Vertice b1 = poligonoB.get(j);
                Vertice b2 = poligonoB.get((j + 1) % nB);

                if (segmentosSeCruzanEstrictamente(a1, a2, b1, b2)) {
                    return true;
                }
            }
        }

        // 3. Verificar si un polígono está completamente contenido dentro del otro (Ray-Casting)
        for (Vertice vA : poligonoA) {
            if (puntoDentroDePoligono(vA, poligonoB)) {
                return true;
            }
        }

        for (Vertice vB : poligonoB) {
            if (puntoDentroDePoligono(vB, poligonoA)) {
                return true;
            }
        }

        // 4. Verificar si el centroide de A cae dentro de B (para casos donde todos los vértices tocan bordes)
        Vertice centroA = ShoelaceCalculator.calcularCentroide(poligonoA);
        if (puntoDentroDePoligono(centroA, poligonoB)) {
            return true;
        }

        return false;
    }

    /**
     * Alias con convención estándar lowerCamelCase para PoligonosSeSolapan.
     */
    public static boolean poligonosSeSolapan(List<Vertice> poligonoA, List<Vertice> poligonoB) {
        return PoligonosSeSolapan(poligonoA, poligonoB);
    }

    /**
     * Verifica si un polígono se cruza consigo mismo (polígono complejo o en forma de "8").
     */
    public static boolean tieneAutoInterseccion(List<Vertice> vertices) {
        if (vertices == null || vertices.size() < 4) {
            return false;
        }

        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            Vertice a1 = vertices.get(i);
            Vertice a2 = vertices.get((i + 1) % n);

            for (int j = i + 2; j < n; j++) {
                // Evitar comparar aristas adyacentes que comparten un vértice legítimo
                if (i == 0 && j == n - 1) {
                    continue;
                }
                Vertice b1 = vertices.get(j);
                Vertice b2 = vertices.get((j + 1) % n);

                if (Line2D.linesIntersect(a1.getX(), a1.getY(), a2.getX(), a2.getY(),
                                          b1.getX(), b1.getY(), b2.getX(), b2.getY())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Algoritmo Ray-Casting (Jordan Curve Theorem) para evaluar si un punto está estrictamente dentro del polígono.
     */
    public static boolean puntoDentroDePoligono(Vertice punto, List<Vertice> poligono) {
        boolean dentro = false;
        int n = poligono.size();
        double px = punto.getX();
        double py = punto.getY();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = poligono.get(i).getX(), yi = poligono.get(i).getY();
            double xj = poligono.get(j).getX(), yj = poligono.get(j).getY();

            // Si el punto está exactamente sobre un vértice, no lo contamos como invasión interna estricta
            if (Math.abs(px - xi) < EPSILON && Math.abs(py - yi) < EPSILON) {
                return false;
            }

            boolean intersecta = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi + EPSILON) + xi);
            if (intersecta) {
                dentro = !dentro;
            }
        }
        return dentro;
    }

    private static boolean segmentosSeCruzanEstrictamente(Vertice a1, Vertice a2, Vertice b1, Vertice b2) {
        double d1 = orientacion(b1, b2, a1);
        double d2 = orientacion(b1, b2, a2);
        double d3 = orientacion(a1, a2, b1);
        double d4 = orientacion(a1, a2, b2);

        return (((d1 > EPSILON && d2 < -EPSILON) || (d1 < -EPSILON && d2 > EPSILON)) &&
                ((d3 > EPSILON && d4 < -EPSILON) || (d3 < -EPSILON && d4 > EPSILON)));
    }

    private static double orientacion(Vertice p, Vertice q, Vertice r) {
        return (q.getX() - p.getX()) * (r.getY() - p.getY()) -
               (q.getY() - p.getY()) * (r.getX() - p.getX());
    }

    private static boolean boundingBoxesSeCruzan(List<Vertice> polyA, List<Vertice> polyB) {
        double minXA = Double.MAX_VALUE, minYA = Double.MAX_VALUE;
        double maxXA = -Double.MAX_VALUE, maxYA = -Double.MAX_VALUE;
        for (Vertice v : polyA) {
            minXA = Math.min(minXA, v.getX());
            minYA = Math.min(minYA, v.getY());
            maxXA = Math.max(maxXA, v.getX());
            maxYA = Math.max(maxYA, v.getY());
        }

        double minXB = Double.MAX_VALUE, minYB = Double.MAX_VALUE;
        double maxXB = -Double.MAX_VALUE, maxYB = -Double.MAX_VALUE;
        for (Vertice v : polyB) {
            minXB = Math.min(minXB, v.getX());
            minYB = Math.min(minYB, v.getY());
            maxXB = Math.max(maxXB, v.getX());
            maxYB = Math.max(maxYB, v.getY());
        }

        return (maxXA >= minXB && minXA <= maxXB && maxYA >= minYB && minYA <= maxYB);
    }

    // ─── Métodos de compatibilidad previa con controladores y pruebas (JTS) ───

    /**
     * Convierte una lista de vértices a un Polygon de JTS.
     */
    public static Polygon construirPoligono(List<Vertice> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException(
                "Se necesitan al menos 3 vértices para formar un polígono.");
        }

        List<Vertice> ordenados = vertices.stream()
            .sorted(Comparator.comparingInt(Vertice::getOrden))
            .toList();

        Coordinate[] coords = new Coordinate[ordenados.size() + 1];

        for (int i = 0; i < ordenados.size(); i++) {
            Vertice v = ordenados.get(i);
            coords[i] = new Coordinate(v.getCoordX(), v.getCoordY());
        }
        coords[ordenados.size()] = coords[0];

        return FACTORY.createPolygon(coords);
    }

    /**
     * Verifica si el polígono nuevo se solapa con alguno de los polígonos existentes.
     */
    public static boolean existeSolapamiento(Polygon nuevo, List<Polygon> existentes) {
        for (Polygon existente : existentes) {
            if (nuevo.intersects(existente)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Devuelve los índices de la lista de existentes que entran en conflicto.
     */
    public static List<Integer> indicesEnConflicto(Polygon nuevo, List<Polygon> existentes) {
        return java.util.stream.IntStream.range(0, existentes.size())
            .filter(i -> nuevo.intersects(existentes.get(i)))
            .boxed()
            .toList();
    }
}
