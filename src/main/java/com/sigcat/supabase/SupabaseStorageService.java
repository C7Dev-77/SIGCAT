package com.sigcat.supabase;

import com.sigcat.dao.DocumentoDAO;
import com.sigcat.models.DocumentoLegal;
import io.github.cdimascio.dotenv.Dotenv;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/**
 * Servicio para gestión y transferencia de archivos en Supabase Storage.
 * Utiliza java.net.http.HttpClient (Java 17 nativo) para subida y descarga de archivos.
 */
public class SupabaseStorageService {

    public static final String BUCKET_DOCUMENTOS = "documentos-legales";
    public static final String BUCKET_AVATARES = "avatares-usuarios";

    private final String supabaseUrl;
    private final String anonKey;
    private final HttpClient httpClient;
    private final DocumentoDAO documentoDAO;

    public SupabaseStorageService() {
        this.supabaseUrl = SupabaseConfig.getSupabaseUrl();
        this.anonKey = SupabaseConfig.getAnonKey();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.documentoDAO = new DocumentoDAO();
    }

    /**
     * Sube un archivo a Supabase Storage y registra su metadato en la base de datos PostgreSQL.
     * Si no hay conexión o falla la nube, aplica persistencia local (Offline-First).
     *
     * @param archivo      Archivo local a subir (PDF, JPG, PNG)
     * @param predioId     ID del predio al que pertenece el soporte
     * @param propietarioId UUID del propietario (opcional)
     * @param tipoDoc      Tipo de documento (ESCRITURA_PUBLICA, RECIBO_VALIDACION, etc.)
     * @param descripcion  Observación o nota aclaratoria
     * @return El objeto DocumentoLegal persistido con su URL pública
     */
    public DocumentoLegal subirDocumento(File archivo, int predioId, String propietarioId, String tipoDoc, String descripcion) throws IOException, InterruptedException {
        if (archivo == null || !archivo.exists()) {
            throw new IllegalArgumentException("El archivo a subir no existe o es nulo.");
        }

        // Validar tamaño máximo (Mejora #7: 10 MB)
        if (archivo.length() > 10 * 1024 * 1024) {
            throw new IOException("El archivo supera el tamaño máximo permitido de 10 MB.");
        }

        // Sanitizar y generar nombre único
        String extension = obtenerExtension(archivo.getName());
        String nombreUnico = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitizarNombre(archivo.getName());
        String storagePath = "predio_" + predioId + "/" + nombreUnico;
        String publicUrl = null;

        // 1. Intentar subida a Supabase Storage Cloud si la URL y key están configuradas
        if (supabaseUrl != null && !supabaseUrl.isBlank() && anonKey != null && !anonKey.isBlank()) {
            try {
                String endpointUpload = supabaseUrl + "/storage/v1/object/" + BUCKET_DOCUMENTOS + "/" + storagePath;
                String mimeType = detectarMimeType(archivo);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpointUpload))
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer " + anonKey)
                        .header("Content-Type", mimeType)
                        .header("x-upsert", "true")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofFile(archivo.toPath()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    publicUrl = supabaseUrl + "/storage/v1/object/public/" + BUCKET_DOCUMENTOS + "/" + storagePath;
                    System.out.println("[STORAGE] Archivo subido exitosamente a Supabase: " + publicUrl);
                } else {
                    System.err.println("[STORAGE] Aviso respuesta Supabase (Status " + response.statusCode() + "): " + response.body());
                }
            } catch (Exception e) {
                System.err.println("[STORAGE] Excepción de red con Supabase Storage: " + e.getMessage());
            }
        }

        // 2. Respaldo local Offline-First en el directorio data/documentos/
        File dirLocal = new File("data/documentos/predio_" + predioId);
        if (!dirLocal.exists()) {
            dirLocal.mkdirs();
        }
        File archivoLocal = new File(dirLocal, nombreUnico);
        try {
            Files.copy(archivo.toPath(), archivoLocal.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("[STORAGE] Aviso al copiar archivo local: " + e.getMessage());
        }

        if (publicUrl == null) {
            publicUrl = archivoLocal.toURI().toString();
        }

        DocumentoLegal doc = new DocumentoLegal(predioId, tipoDoc, archivo.getName(), storagePath, publicUrl);
        doc.setPropietarioId(propietarioId);
        doc.setDescripcion(descripcion);
        try {
            doc.setHashSha256(com.sigcat.security.DocumentoSecurityUtil.calcularSHA256(archivo));
        } catch (Exception ignored) {}

        boolean guardado = documentoDAO.insertar(doc);
        if (!guardado) {
            System.err.println("[STORAGE] Falló registro en tabla documentos.");
        }
        return doc;
    }

    /**
     * Sube un avatar de usuario al bucket de avatares.
     *
     * @param archivo  Imagen seleccionada (JPG, PNG)
     * @param usuarioId Documento o ID del usuario
     * @return URL pública del avatar
     */
    public String subirAvatar(File archivo, String usuarioId) throws IOException, InterruptedException {
        if (archivo == null || !archivo.exists()) return null;

        String extension = obtenerExtension(archivo.getName());
        String storagePath = "usr_" + usuarioId + "/avatar_" + System.currentTimeMillis() + extension;
        String endpointUpload = supabaseUrl + "/storage/v1/object/" + BUCKET_AVATARES + "/" + storagePath;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUpload))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + anonKey)
                .header("Content-Type", detectarMimeType(archivo))
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofFile(archivo.toPath()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return supabaseUrl + "/storage/v1/object/public/" + BUCKET_AVATARES + "/" + storagePath;
        }
        throw new IOException("Error subiendo avatar (Status " + response.statusCode() + "): " + response.body());
    }

    /**
     * Sube un arreglo de bytes directamente a cualquier bucket de Supabase Storage.
     */
    public String subirArchivoDirecto(String bucket, String storagePath, byte[] contenido, String mimeType) throws IOException, InterruptedException {
        if (supabaseUrl == null || anonKey == null) {
            return "https://local-offline/storage/" + bucket + "/" + storagePath;
        }
        String endpointUpload = supabaseUrl + "/storage/v1/object/" + bucket + "/" + storagePath;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUpload))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + anonKey)
                .header("Content-Type", mimeType)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(contenido))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + storagePath;
        }
        throw new IOException("Fallo al subir archivo directo a Supabase Storage (Status " + response.statusCode() + "): " + response.body());
    }

    /**
     * Descarga y abre un documento con la aplicación nativa del sistema operativo (visor de PDF o visor de imágenes).
     *
     * @param storageUrl URL pública del documento
     */
    public void descargarYAbrirDocumento(String storageUrl, String nombreOriginal) {
        new Thread(() -> {
            try {
                if (storageUrl == null || storageUrl.isBlank()) return;

                // Si es un archivo local offline o URI file:
                if (storageUrl.startsWith("file:") || new File(storageUrl).exists()) {
                    File localFile = storageUrl.startsWith("file:") ? new File(URI.create(storageUrl)) : new File(storageUrl);
                    if (localFile.exists() && Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(localFile);
                        return;
                    }
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(storageUrl))
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer " + anonKey)
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                Path tempFile = Files.createTempFile("sigcat_doc_", "_" + sanitizarNombre(nombreOriginal != null ? nombreOriginal : "documento.pdf"));
                tempFile.toFile().deleteOnExit();

                HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE));

                if (response.statusCode() == 200 && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(tempFile.toFile());
                } else {
                    System.err.println("[STORAGE] Error al descargar archivo para visualización. Código: " + response.statusCode());
                }
            } catch (Exception e) {
                System.err.println("[STORAGE] Error al abrir documento localmente: " + e.getMessage());
            }
        }).start();
    }

    private String detectarMimeType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private String obtenerExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(idx) : "";
    }

    private String sanitizarNombre(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

