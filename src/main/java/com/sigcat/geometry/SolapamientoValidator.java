package com.sigcat.geometry;

import com.sigcat.models.Vertice;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.Comparator;
import java.util.List;

/**
 * Detector de solapamiento entre predios usando JTS Topology Suite.
 * Convierte los vértices almacenados en la base de datos a geometrías
 * JTS para validar si dos predios invaden el mismo territorio.
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class SolapamientoValidator {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    /**
     * Convierte una lista de vértices (en el orden guardado en BD) a un
     * Polygon de JTS, cerrando el anillo automáticamente.
     */
    public static Polygon construirPoligono(List<Vertice> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException(
                "Se necesitan al menos 3 vértices para formar un polígono.");
        }

        List<Vertice> ordenados = vertices.stream()
            .sorted(Comparator.comparingInt(Vertice::getOrden))
            .toList();

        // +1 para el punto de cierre (JTS exige que el primer y último
        // punto del anillo sean idénticos)
        Coordinate[] coords = new Coordinate[ordenados.size() + 1];

        for (int i = 0; i < ordenados.size(); i++) {
            Vertice v = ordenados.get(i);
            coords[i] = new Coordinate(v.getCoordX(), v.getCoordY());
        }
        coords[ordenados.size()] = coords[0]; // cerrar el anillo

        return FACTORY.createPolygon(coords);
    }

    /**
     * Verifica si el polígono nuevo se solapa con alguno de los polígonos
     * ya existentes (predios aprobados).
     *
     * @return true si hay conflicto con al menos uno.
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
     * Igual que existeSolapamiento, pero te dice CUÁLES índices de la lista
     * de existentes entran en conflicto (útil para mostrarle al funcionario
     * exactamente qué predio se solapa).
     */
    public static List<Integer> indicesEnConflicto(Polygon nuevo, List<Polygon> existentes) {
        return java.util.stream.IntStream.range(0, existentes.size())
            .filter(i -> nuevo.intersects(existentes.get(i)))
            .boxed()
            .toList();
    }
}