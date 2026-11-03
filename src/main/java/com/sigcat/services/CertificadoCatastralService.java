package com.sigcat.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.sigcat.dao.DocumentoDAO;
import com.sigcat.models.DocumentoLegal;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;
import com.sigcat.models.Vertice;
import com.sigcat.security.DocumentoSecurityUtil;
import com.sigcat.supabase.SupabaseStorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio corporativo para la generación y firma de Certificados Catastrales en PDF (Mejoras #3 y #9).
 * Utiliza Apache PDFBox para construir el documento institucional oficial y ZXing para incrustar
 * códigos QR criptográficos de validación ciudadana.
 */
public class CertificadoCatastralService {

    private final SupabaseStorageService storageService;
    private final DocumentoDAO documentoDAO;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public CertificadoCatastralService() {
        this.storageService = new SupabaseStorageService();
        this.documentoDAO = new DocumentoDAO();
    }

    public CertificadoCatastralService(SupabaseStorageService storageService, DocumentoDAO documentoDAO) {
        this.storageService = storageService;
        this.documentoDAO = documentoDAO;
    }

    /**
     * Genera en memoria los bytes de una imagen PNG con un código QR.
     */
    public static byte[] generarCodigoQRPNG(String contenido, int ancho, int alto) throws Exception {
        QRCodeWriter qrWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix bitMatrix = qrWriter.encode(contenido, BarcodeFormat.QR_CODE, ancho, alto, hints);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Construye el documento PDF oficial del Certificado de Incorporación Catastral.
     */
    public byte[] generarCertificadoPDFBytes(Predio predio, Usuario funcionario, Usuario propietario) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            String radicado = "CC-70001-" + (predio.getCodigoCatastral() != null ? predio.getCodigoCatastral() : String.valueOf(predio.getId()));
            String fechaExpedicion = LocalDateTime.now().format(FORMATO_FECHA);
            String urlVerificacion = "https://sigcat.sincelejo.gov.co/validar?radicado=" + radicado;

            // Generar QR
            byte[] qrBytes;
            try {
                qrBytes = generarCodigoQRPNG(urlVerificacion, 120, 120);
            } catch (Exception e) {
                qrBytes = new byte[0];
            }

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float startX = 40;
                float width = page.getMediaBox().getWidth() - 80;
                float y = page.getMediaBox().getHeight() - 40;

                // 1. Marco decorativo institucional
                cs.setStrokingColor(40, 53, 147); // Azul marino institucional
                cs.setLineWidth(2f);
                cs.addRect(startX, 35, width, page.getMediaBox().getHeight() - 75);
                cs.stroke();

                // 2. Encabezado institucional
                cs.setNonStrokingColor(30, 41, 59);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(startX + 15, y);
                cs.showText("REPÚBLICA DE COLOMBIA — DEPARTAMENTO DE SUCRE");
                cs.endText();

                y -= 15;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                cs.newLineAtOffset(startX + 15, y);
                cs.showText("ALCALDÍA MUNICIPAL DE SINCELEJO | CÓDIGO DANE 70001");
                cs.endText();

                y -= 14;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(startX + 15, y);
                cs.showText("SECRETARÍA DE PLANEACIÓN Y DIRECCIÓN DE GESTIÓN CATASTRAL");
                cs.endText();

                // Línea divisoria
                y -= 10;
                cs.setStrokingColor(203, 213, 225);
                cs.setLineWidth(1f);
                cs.moveTo(startX + 15, y);
                cs.lineTo(startX + width - 15, y);
                cs.stroke();

                // 3. Título del Certificado
                y -= 25;
                cs.setNonStrokingColor(16, 185, 129); // Verde esmeralda oficial
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
                cs.newLineAtOffset(startX + 15, y);
                cs.showText("CERTIFICADO OFICIAL DE INCORPORACIÓN Y VALIDEZ CATASTRAL");
                cs.endText();

                y -= 15;
                cs.setNonStrokingColor(100, 116, 139);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 9);
                cs.newLineAtOffset(startX + 15, y);
                cs.showText("Radicado No.: " + radicado + "  |  Fecha de Expedición: " + fechaExpedicion);
                cs.endText();

                y -= 18;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 8.5f);
                cs.newLineAtOffset(startX + 15, y);
                cs.showText("Expedido conforme a la Ley 14 de 1983, Ley 1955 de 2019 y Resolución IGAC 1149 de 2021.");
                cs.endText();

                // 4. Tabla de Datos del Predio
                y -= 20;
                dibujarSeccionHeader(cs, startX + 15, y, width - 30, "1. IDENTIFICACIÓN Y DATOS GENERALES DEL INMUEBLE");

                y -= 20;
                String titular = (propietario != null && propietario.getNombre() != null) ? propietario.getNombre() : (predio.getNombrePropietario() != null ? predio.getNombrePropietario() : "Sin asignar");
                String docTitular = (propietario != null && propietario.getDocumento() != null) ? propietario.getDocumento() : "N/A";

                dibujarFila(cs, startX + 20, y, "Código Catastral:", predio.getCodigoCatastral() != null ? predio.getCodigoCatastral() : "Pendiente");
                y -= 16;
                dibujarFila(cs, startX + 20, y, "Nombre del Inmueble:", predio.getNombre() != null ? predio.getNombre() : "Predio Particular");
                y -= 16;
                dibujarFila(cs, startX + 20, y, "Dirección / Ubicación:", predio.getDireccion() != null ? predio.getDireccion() : "Municipio de Sincelejo, Sucre");
                y -= 16;
                dibujarFila(cs, startX + 20, y, "Propietario / Titular:", titular + " (Identificación: " + docTitular + ")");
                y -= 16;
                dibujarFila(cs, startX + 20, y, "Uso del Suelo / Destinación:", predio.getUsoSuelo() != null ? predio.getUsoSuelo() : "RESIDENCIAL / COMERCIAL");
                y -= 16;
                dibujarFila(cs, startX + 20, y, "Tipo de Régimen:", predio.getTipo() != null ? predio.getTipo() : "PRIVADO");
                y -= 16;
                String areaStr = String.format(Locale.ROOT, "%.2f m²", predio.getAreaCalculada());
                dibujarFila(cs, startX + 20, y, "Área Superficial Calculada:", areaStr);
                y -= 16;
                dibujarFila(cs, startX + 20, y, "Estado Jurídico-Catastral:", "APROBADO Y REGISTRADO EN CARTOGRAFÍA OFICIAL");

                // 5. Delimitación de Vértices y Geometría
                y -= 24;
                dibujarSeccionHeader(cs, startX + 15, y, width - 30, "2. DELIMITACIÓN GEOMÉTRICA (SISTEMA DE COORDENADAS WGS84)");

                y -= 18;
                List<Vertice> vertices = predio.getVertices();
                if (vertices != null && !vertices.isEmpty()) {
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 8.5f);
                    cs.setNonStrokingColor(30, 41, 59);
                    cs.beginText();
                    cs.newLineAtOffset(startX + 20, y);
                    cs.showText("Vértice    |   Longitud (X)    |   Latitud (Y)      |    Sistema de Referencia");
                    cs.endText();

                    cs.setFont(PDType1Font.HELVETICA, 8.5f);
                    int limite = Math.min(vertices.size(), 6);
                    for (int i = 0; i < limite; i++) {
                        y -= 13;
                        Vertice v = vertices.get(i);
                        cs.beginText();
                        cs.newLineAtOffset(startX + 20, y);
                        cs.showText(String.format(Locale.ROOT, "V-%02d       |   %.6f    |   %.6f     |    EPSG:4326 (WGS84)", v.getOrden(), v.getCoordX(), v.getCoordY()));
                        cs.endText();
                    }
                    if (vertices.size() > 6) {
                        y -= 12;
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 8f);
                        cs.newLineAtOffset(startX + 20, y);
                        cs.showText("... Polígono con " + vertices.size() + " vértices validados contra solapamiento en el repositorio SIGCAT.");
                        cs.endText();
                    }
                } else {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 8.5f);
                    cs.newLineAtOffset(startX + 20, y);
                    cs.showText("Georreferenciación digital registrada en el sistema de coordenadas municipales.");
                    cs.endText();
                }

                // 6. Incrustar Código QR y Firma Digital
                y -= 28;
                if (qrBytes.length > 0) {
                    PDImageXObject qrImage = PDImageXObject.createFromByteArray(document, qrBytes, "QR_VERIFICACION");
                    float qrSize = 95;
                    float qrX = startX + width - qrSize - 20;
                    float qrY = 55;
                    cs.drawImage(qrImage, qrX, qrY, qrSize, qrSize);

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 7f);
                    cs.setNonStrokingColor(100, 116, 139);
                    cs.newLineAtOffset(qrX - 5, qrY - 10);
                    cs.showText("ESCANEAR PARA VALIDAR");
                    cs.endText();
                }

                // Texto de certificación legal
                float pieY = 120;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
                cs.setNonStrokingColor(30, 41, 59);
                cs.newLineAtOffset(startX + 20, pieY);
                cs.showText("CONSTANCIA DE FIRMA Y VALIDEZ DIGITAL");
                cs.endText();

                pieY -= 14;
                String nombreFuncionario = (funcionario != null && funcionario.getNombre() != null) ? funcionario.getNombre() : "Ing. Funcionario de Catastro";
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 8.5f);
                cs.newLineAtOffset(startX + 20, pieY);
                cs.showText("Aprobado y expedido por: " + nombreFuncionario);
                cs.endText();

                pieY -= 12;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 8.5f);
                cs.newLineAtOffset(startX + 20, pieY);
                cs.showText("Dirección Técnica de Catastro y Cartografía Municipal — Sincelejo");
                cs.endText();

                pieY -= 16;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 7.5f);
                cs.setNonStrokingColor(100, 116, 139);
                cs.newLineAtOffset(startX + 20, pieY);
                cs.showText("La integridad de este certificado puede verificarse mediante su firma criptográfica SHA-256.");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private void dibujarSeccionHeader(PDPageContentStream cs, float x, float y, float w, String titulo) throws IOException {
        cs.setNonStrokingColor(241, 245, 249);
        cs.addRect(x, y - 4, w, 16);
        cs.fill();

        cs.setNonStrokingColor(30, 41, 59);
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9.5f);
        cs.newLineAtOffset(x + 5, y);
        cs.showText(titulo);
        cs.endText();
    }

    private void dibujarFila(PDPageContentStream cs, float x, float y, String etiqueta, String valor) throws IOException {
        cs.setFont(PDType1Font.HELVETICA_BOLD, 8.5f);
        cs.setNonStrokingColor(71, 85, 105);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(etiqueta);
        cs.endText();

        cs.setFont(PDType1Font.HELVETICA, 8.5f);
        cs.setNonStrokingColor(15, 23, 42);
        cs.beginText();
        cs.newLineAtOffset(x + 145, y);
        cs.showText(valor);
        cs.endText();
    }

    /**
     * Genera el Certificado Catastral PDF, calcula su hash SHA-256, lo sube a Supabase Storage
     * y lo registra en la base de datos de documentos.
     */
    public DocumentoLegal generarYGuardarCertificado(Predio predio, Usuario funcionario, Usuario propietario) throws Exception {
        byte[] pdfBytes = generarCertificadoPDFBytes(predio, funcionario, propietario);
        String hashSha256 = DocumentoSecurityUtil.calcularSHA256(pdfBytes);

        String idDoc = UUID.randomUUID().toString();
        String nombreArchivo = "Certificado_Catastral_" + (predio.getCodigoCatastral() != null ? predio.getCodigoCatastral() : predio.getId()) + ".pdf";
        String storagePath = "certificados/" + predio.getId() + "/" + nombreArchivo;

        // Subir a Supabase Storage
        String storageUrl = storageService.subirArchivoDirecto("documentos-legales", storagePath, pdfBytes, "application/pdf");

        DocumentoLegal doc = new DocumentoLegal();
        doc.setId(idDoc);
        doc.setPredioId(predio.getId());
        if (propietario != null) {
            doc.setPropietarioId(String.valueOf(propietario.getId()));
        }
        doc.setTipoDocumento("CERTIFICADO_CATASTRO");
        doc.setNombreArchivo(nombreArchivo);
        doc.setStoragePath(storagePath);
        doc.setStorageUrl(storageUrl);
        doc.setEstado("APROBADO");
        doc.setDescripcion("Certificado Oficial de Incorporación Catastral generado por la Alcaldía de Sincelejo.");
        doc.setHashSha256(hashSha256);
        if (funcionario != null) {
            doc.setRevisadoPor(String.valueOf(funcionario.getId()));
        }

        documentoDAO.insertar(doc);
        return doc;
    }

    /**
     * Exporta el Certificado a un archivo PDF físico en la ruta indicada por el usuario.
     */
    public File exportarCertificadoAArchivo(Predio predio, Usuario funcionario, Usuario propietario, File destino) throws IOException {
        byte[] pdfBytes = generarCertificadoPDFBytes(predio, funcionario, propietario);
        try (FileOutputStream fos = new FileOutputStream(destino)) {
            fos.write(pdfBytes);
        }
        return destino;
    }
}
