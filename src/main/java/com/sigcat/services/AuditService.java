package com.sigcat.services;

import com.sigcat.dao.AuditLogDAO;
import com.sigcat.models.AuditLog;
import com.sigcat.models.DocumentoLegal;
import com.sigcat.models.Predio;
import com.sigcat.models.Usuario;

/**
 * Servicio de auditoría formal para registrar eventos de mutación catastral (Mejora #4).
 */
public class AuditService {

    private final AuditLogDAO auditLogDAO;

    public AuditService() {
        this.auditLogDAO = new AuditLogDAO();
    }

    public AuditService(AuditLogDAO auditLogDAO) {
        this.auditLogDAO = auditLogDAO;
    }

    public void auditarCreacionPredio(Predio predio, Usuario usuario) {
        String uId = usuario != null ? String.valueOf(usuario.getId()) : "SISTEMA";
        String uNom = usuario != null ? usuario.getNombre() : "Usuario Anónimo";
        AuditLog log = new AuditLog(
            "PREDIO",
            String.valueOf(predio.getId()),
            "CREACION",
            null,
            predio.getEstado(),
            uId,
            uNom,
            "Radicación de nuevo predio: " + predio.getCodigoCatastral() + " (" + predio.getNombre() + ")"
        );
        new Thread(() -> auditLogDAO.registrar(log)).start();
    }

    public void auditarCambioEstadoPredio(Predio predio, String estadoAnterior, String estadoNuevo, Usuario funcionario, String motivo) {
        String uId = funcionario != null ? String.valueOf(funcionario.getId()) : "FUNCIONARIO";
        String uNom = funcionario != null ? funcionario.getNombre() : "Funcionario Catastral";
        AuditLog log = new AuditLog(
            "PREDIO",
            String.valueOf(predio.getId()),
            "APROBADO".equalsIgnoreCase(estadoNuevo) ? "APROBACION" : "RECHAZO",
            estadoAnterior,
            estadoNuevo,
            uId,
            uNom,
            "Predio " + predio.getCodigoCatastral() + " pasó de " + estadoAnterior + " a " + estadoNuevo + ". " + (motivo != null ? "Motivo: " + motivo : "")
        );
        new Thread(() -> auditLogDAO.registrar(log)).start();
    }

    public void auditarDocumentoSubido(DocumentoLegal doc, Usuario usuario) {
        String uId = usuario != null ? String.valueOf(usuario.getId()) : "PROPIETARIO";
        String uNom = usuario != null ? usuario.getNombre() : "Propietario";
        AuditLog log = new AuditLog(
            "DOCUMENTO",
            doc.getId(),
            "SUBIDA_DOCUMENTO",
            null,
            doc.getEstado(),
            uId,
            uNom,
            "Subida de " + doc.getTipoDocumento() + " (" + doc.getNombreArchivo() + ") para el predio ID " + doc.getPredioId()
        );
        new Thread(() -> auditLogDAO.registrar(log)).start();
    }
}

