package com.sigcat.models;

/**
 * Modelo de dominio que representa a un usuario del sistema SIGCAT.
 * Roles posibles: PROPIETARIO, FUNCIONARIO, ADMIN.
 *
 * Responsable: Aldo Ibañez (Persistencia)
 * Actualizado: Evolución v2.0 — Perfil de Propietario y Autenticación Supabase
 */
public class Usuario {

    public enum Rol {
        PROPIETARIO,
        FUNCIONARIO,
        ADMIN
    }

    private int id;
    private String documento;
    private String nombre;
    private String password;
    private Rol rol = Rol.PROPIETARIO;
    private String email;
    private String telefono;
    private String avatarUrl;

    public Usuario() {
    }

    public Usuario(String documento, String nombre, String password, Rol rol) {
        this.documento = documento;
        this.nombre = nombre;
        this.password = password;
        this.rol = rol;
    }

    public Usuario(String documento, String nombre, String password, String rol) {
        this.documento = documento;
        this.nombre = nombre;
        this.password = password;
        if (rol != null) {
            try {
                this.rol = Rol.valueOf(rol.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                this.rol = Rol.PROPIETARIO;
            }
        }
    }

    public Usuario(int id, String documento, String nombre, String password, Rol rol) {
        this.id = id;
        this.documento = documento;
        this.nombre = nombre;
        this.password = password;
        this.rol = rol;
    }

    // ─── Getters y Setters ───────────────────────────────────────
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordHash() {
        return password;
    }

    public void setPasswordHash(String passwordHash) {
        this.password = passwordHash;
    }

    public String getRol() {
        return rol != null ? rol.name() : "PROPIETARIO";
    }

    public Rol getRolEnum() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public void setRol(String rolStr) {
        if (rolStr != null) {
            try {
                this.rol = Rol.valueOf(rolStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                this.rol = Rol.PROPIETARIO;
            }
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", documento='" + documento + "', nombre='" + nombre + "', rol=" + rol + "}";
    }
}
