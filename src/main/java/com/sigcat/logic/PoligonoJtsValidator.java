package com.sigcat.logic;

import javafx.geometry.Point2D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Fase 3: conversión de los vértices capturados en el Canvas (JavaFX) a un
 * objeto Polygon de JTS, y verificación de solapamiento contra los predios
 * ya aprobados.
 *
 * Implementado por: Leider Barreto (UI/UX)
 *
 * Dependencia en pom.xml (ya incluida):
 *   <groupId>org.locationtech.jts</groupId>
 *   <artifactId>jts-core</artifactId>
 *   <version>1.20.0</version>
 */
public class PoligonoJtsValidator {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    /**
     * Convierte la lista de vértices dibujados en el Canvas (en orden) a un
     * Polygon de JTS. Cierra el anillo automáticamente repitiendo el primer punto.
     *
     * @param verticesCanvas lista de puntos en coordenadas del Canvas JavaFX.
     * @return Polygon de JTS listo para operaciones espaciales.
     */
    public Polygon crearPoligono(List<Point2D> verticesCanvas) {
        if (verticesCanvas.size() < 3) {
            throw new IllegalArgumentException("Se necesitan al menos 3 vértices para formar un polígono.");
        }

        List<Coordinate> coordenadas = new ArrayList<>();
        for (Point2D punto : verticesCanvas) {
            coordenadas.add(new Coordinate(punto.getX(), punto.getY()));
        }
        // JTS exige que un anillo (ring) esté cerrado: el primer y último punto deben coincidir.
        coordenadas.add(coordenadas.get(0));

        Coordinate[] arregloCoordenadas = coordenadas.toArray(new Coordinate[0]);
        return FACTORY.createPolygon(arregloCoordenadas);
    }

    /**
     * @param poligonoNuevo      el predio que el propietario está intentando registrar.
     * @param poligonosAprobados predios ya aprobados en el sistema (obtenidos de la BD por Aldo).
     * @return true si el nuevo predio invade (intersecta) algún predio ya aprobado.
     */
    public boolean existeSolapamiento(Polygon poligonoNuevo, List<Polygon> poligonosAprobados) {
        for (Polygon aprobado : poligonosAprobados) {
            if (poligonoNuevo.intersects(aprobado)) {
                return true;
            }
        }
        return false;
    }
}
