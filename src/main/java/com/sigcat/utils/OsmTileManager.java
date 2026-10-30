package com.sigcat.utils;

import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Motor cartográfico OpenStreetMap 100% nativo en Java (sin HTML ni WebView).
 * Centrado por defecto en Sincelejo, Sucre, Colombia.
 */
public class OsmTileManager {

    private static final int TILE_SIZE = 256;
    private static final String OSM_TILE_SERVER = "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final String USER_AGENT = "SIGCAT-Sincelejo-JavaFX/1.0 (Catastro Sucre)";

    public static final double SINCELEJO_CENTRO_LAT = 9.30472;
    public static final double SINCELEJO_CENTRO_LON = -75.39778;
    public static final int DEFAULT_ZOOM_CATASTRAL = 17;

    private final Map<String, Image> tileCache = new ConcurrentHashMap<>();
    private final Set<String> downloadingTiles = ConcurrentHashMap.newKeySet();
    private final ExecutorService downloaderPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "OSM-Sincelejo-TileDownloader");
        t.setDaemon(true);
        return t;
    });

    private double centerLat = SINCELEJO_CENTRO_LAT;
    private double centerLon = SINCELEJO_CENTRO_LON;
    private int zoom = DEFAULT_ZOOM_CATASTRAL;

    private final Runnable onTileLoadedCallback;

    public OsmTileManager(Runnable onTileLoadedCallback) {
        this.onTileLoadedCallback = onTileLoadedCallback;
    }

    public void centrarEnSincelejo() {
        this.centerLat = SINCELEJO_CENTRO_LAT;
        this.centerLon = SINCELEJO_CENTRO_LON;
        this.zoom = DEFAULT_ZOOM_CATASTRAL;
        if (onTileLoadedCallback != null) {
            Platform.runLater(onTileLoadedCallback);
        }
    }

    public void irASectorSincelejo(String nombreSector) {
        if (nombreSector == null) return;
        switch (nombreSector) {
            case "Centro Histórico (Parque Santander)":
                setCoordenadasCentro(9.30472, -75.39778, 17);
                break;
            case "Sector Norte (Unisucre / Puerta Roja)":
                setCoordenadasCentro(9.31680, -75.38850, 17);
                break;
            case "Sector Venecia / Las Mercedes":
                setCoordenadasCentro(9.29850, -75.38620, 17);
                break;
            case "Sector Majagual / Pozo de Majagual":
                setCoordenadasCentro(9.29980, -75.39990, 17);
                break;
            case "Vista General Urbana Sincelejo":
                setCoordenadasCentro(9.30472, -75.39778, 15);
                break;
            default:
                centrarEnSincelejo();
                break;
        }
    }

    public void renderizarMapaBase(GraphicsContext gc, double canvasWidth, double canvasHeight) {
        double centerTileX = lonToTileX(centerLon, zoom);
        double centerTileY = latToTileY(centerLat, zoom);

        double startX = centerTileX - (canvasWidth / 2.0) / TILE_SIZE;
        double startY = centerTileY - (canvasHeight / 2.0) / TILE_SIZE;

        int minTileX = (int) Math.floor(startX);
        int minTileY = (int) Math.floor(startY);
        int maxTileX = (int) Math.floor(startX + (canvasWidth / TILE_SIZE)) + 1;
        int maxTileY = (int) Math.floor(startY + (canvasHeight / TILE_SIZE)) + 1;

        int maxIndex = (1 << zoom) - 1;

        for (int tx = minTileX; tx <= maxTileX; tx++) {
            for (int ty = minTileY; ty <= maxTileY; ty++) {
                if (tx < 0 || ty < 0 || tx > maxIndex || ty > maxIndex) continue;

                double screenX = (tx - startX) * TILE_SIZE;
                double screenY = (ty - startY) * TILE_SIZE;

                Image tileImg = obtenerTesela(zoom, tx, ty);
                if (tileImg != null && !tileImg.isError()) {
                    gc.drawImage(tileImg, screenX, screenY, TILE_SIZE, TILE_SIZE);
                } else {
                    dibujarPlaceholderCarga(gc, screenX, screenY);
                }
            }
        }

        dibujarHUDCoordenadas(gc, canvasWidth, canvasHeight);
    }

    private void dibujarHUDCoordenadas(GraphicsContext gc, double canvasWidth, double canvasHeight) {
        String infoGeo = String.format("📍 Sincelejo, Sucre | Lat: %.5f° N, Lon: %.5f° O | Zoom: %d | © OpenStreetMap",
                centerLat, centerLon, zoom);

        gc.setFill(Color.rgb(15, 23, 42, 0.78));
        gc.fillRoundRect(10, canvasHeight - 32, 470, 24, 6, 6);

        gc.setFill(Color.web("#f8fafc"));
        gc.setFont(Font.font("System", 11));
        gc.fillText(infoGeo, 18, canvasHeight - 16);
    }

    private Image obtenerTesela(int z, int x, int y) {
        String key = z + "/" + x + "/" + y;
        if (tileCache.containsKey(key)) {
            return tileCache.get(key);
        }

        if (!downloadingTiles.contains(key)) {
            downloadingTiles.add(key);
            downloaderPool.submit(() -> descargarTeselaAsync(z, x, y, key));
        }
        return null;
    }

    private void descargarTeselaAsync(int z, int x, int y, String key) {
        try {
            String urlStr = String.format(OSM_TILE_SERVER, z, x, y);
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (InputStream is = conn.getInputStream()) {
                Image img = new Image(is);
                if (!img.isError()) {
                    tileCache.put(key, img);
                    if (onTileLoadedCallback != null) {
                        Platform.runLater(onTileLoadedCallback);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error tesela OSM [" + key + "]: " + e.getMessage());
        } finally {
            downloadingTiles.remove(key);
        }
    }

    private void dibujarPlaceholderCarga(GraphicsContext gc, double x, double y) {
        gc.setFill(Color.web("#f1f5f9"));
        gc.fillRect(x, y, TILE_SIZE, TILE_SIZE);
        gc.setStroke(Color.web("#cbd5e1"));
        gc.setLineWidth(0.5);
        gc.strokeRect(x, y, TILE_SIZE, TILE_SIZE);
    }

    public void desplazarMapa(double deltaPixelsX, double deltaPixelsY) {
        double centerTileX = lonToTileX(centerLon, zoom) - (deltaPixelsX / TILE_SIZE);
        double centerTileY = latToTileY(centerLat, zoom) - (deltaPixelsY / TILE_SIZE);
        this.centerLon = tileXToLon(centerTileX, zoom);
        this.centerLat = tileYToLat(centerTileY, zoom);
    }

    public void cambiarZoom(int deltaZoom) {
        int nuevoZoom = Math.max(13, Math.min(19, this.zoom + deltaZoom));
        if (nuevoZoom != this.zoom) {
            this.zoom = nuevoZoom;
            if (onTileLoadedCallback != null) {
                Platform.runLater(onTileLoadedCallback);
            }
        }
    }

    public void setCoordenadasCentro(double lat, double lon, int nuevoZoom) {
        this.centerLat = lat;
        this.centerLon = lon;
        this.zoom = Math.max(13, Math.min(19, nuevoZoom));
        if (onTileLoadedCallback != null) {
            Platform.runLater(onTileLoadedCallback);
        }
    }

    /**
     * Convierte un píxel de pantalla del Canvas (screenX, screenY) a coordenadas geográficas WGS84 [lon, lat].
     */
    public double[] screenPixelToGeo(double screenX, double screenY, double canvasWidth, double canvasHeight) {
        double centerTileX = lonToTileX(centerLon, zoom);
        double centerTileY = latToTileY(centerLat, zoom);
        double startX = centerTileX - (canvasWidth / 2.0) / TILE_SIZE;
        double startY = centerTileY - (canvasHeight / 2.0) / TILE_SIZE;

        double tileX = startX + (screenX / TILE_SIZE);
        double tileY = startY + (screenY / TILE_SIZE);

        double lon = tileXToLon(tileX, zoom);
        double lat = tileYToLat(tileY, zoom);
        return new double[]{lon, lat};
    }

    /**
     * Convierte una coordenada geográfica WGS84 (lon, lat) a píxeles de pantalla en el Canvas [screenX, screenY].
     */
    public double[] geoToScreenPixel(double lon, double lat, double canvasWidth, double canvasHeight) {
        double centerTileX = lonToTileX(centerLon, zoom);
        double centerTileY = latToTileY(centerLat, zoom);
        double startX = centerTileX - (canvasWidth / 2.0) / TILE_SIZE;
        double startY = centerTileY - (canvasHeight / 2.0) / TILE_SIZE;

        double tileX = lonToTileX(lon, zoom);
        double tileY = latToTileY(lat, zoom);

        double screenX = (tileX - startX) * TILE_SIZE;
        double screenY = (tileY - startY) * TILE_SIZE;
        return new double[]{screenX, screenY};
    }

    public static double lonToTileX(double lon, int zoom) {
        return ((lon + 180.0) / 360.0) * (1 << zoom);
    }

    public static double latToTileY(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom);
    }

    public static double tileXToLon(double tileX, int zoom) {
        return tileX / (1 << zoom) * 360.0 - 180.0;
    }

    public static double tileYToLat(double tileY, int zoom) {
        double n = Math.PI - (2.0 * Math.PI * tileY) / (1 << zoom);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    public double getCenterLat() { return centerLat; }
    public double getCenterLon() { return centerLon; }
    public int getZoom() { return zoom; }
}
