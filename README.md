# 🗺️ SIGCAT v2.0 — Sistema de Información Geográfica y Catastral de Sincelejo

**Institución:** Universidad Antonio José de Sucre (UNIAJS)  
**Programa:** Ingeniería de Sistemas | **Asignatura:** Ingeniería de Software  
**Docente:** Ing. Juan Manuel Palacio  
**Ubicación Territorial:** Municipio de Sincelejo, Sucre, Colombia (Código DANE: `70001`)

---

## 🚀 Inicio Rápido para Pruebas (¡Solo 1 Clic!)

Para ejecutar el software en tu PC sin necesidad de configurar entornos complejos:

### Opción A — Ejecutable Directo (Recomendada para Pruebas)
1. Asegúrate de tener instalado **Java 17 o superior** ([Descargar Eclipse Temurin Java 17 LTS](https://adoptium.net/temurin/releases/?version=17) si aún no lo tienes).
2. Haz doble clic en el archivo **`iniciar_SIGCAT.bat`** (ubicado en la raíz del proyecto o dentro de la carpeta `ejecutable/`).
3. ¡Listo! La aplicación se iniciará de inmediato en pantalla maximizada.

### Opción B — Desde Código Fuente con Maven
```bash
# En Windows (PowerShell o CMD):
.\mvnw.cmd javafx:run

# En Linux o macOS:
./mvnw javafx:run
```

---

## ☁️ Conexión con Supabase Cloud (Base de Datos Central y Almacenamiento)

El proyecto cuenta con arquitectura **Offline-First**:
* Si tienes internet y configuras el archivo `.env`, el sistema se conectará a **Supabase PostgreSQL** y **Supabase Storage**.
* Si no hay internet o no configuras `.env`, el software funcionará automáticamente en **Modo Local Offline** usando la base de datos embebida `sigcat.db` (SQLite).

### Para conectar tu equipo a la nube compartida:
Copia el archivo `.env.example` y renómbralo a `.env`:
```powershell
copy .env.example .env
```
*(El archivo `.env` ya incluye las credenciales configuradas para el proyecto de Sincelejo).*

---

## 🔑 Credenciales Oficiales de Demostración

| Rol | Cédula / Documento | Contraseña | Vista Inicial |
| :--- | :--- | :--- | :--- |
| **Propietario Catastral** | `2002` | `123` | Portal "Mis Predios", Clic en **"👤 Mi Perfil"** para ver Centro Documental |
| **Funcionario Catastral** | `1001` | `123` | Bandeja de Revisión, Dictamen Técnico y Generación de Certificados |
| **Administrador SIGCAT** | `9999` | `123` | Dashboard Ejecutivo con Gráficos de Cobertura y Métricas |

> 💡 **Autoregistro:** En la pantalla de login también puedes pulsar en *"¿No tienes cuenta? Regístrate aquí"* para registrar un nuevo propietario catastral con cifrado seguro BCrypt.

---

## 🏛️ Nuevas Capacidades Implementadas (v2.0)

1. **📄 Certificados Catastrales PDF con QR y Firma Digital:**  
   Al aprobar un predio, el funcionario genera automáticamente el "Certificado Oficial de Incorporación Catastral — Alcaldía de Sincelejo (DANE 70001)" con código QR de verificación ciudadana y hash criptográfico SHA-256.
2. **🔔 Notificaciones Push en Tiempo Real (WebSocket):**  
   Conexión viva con Supabase Realtime para recibir solicitudes y cambios de estado al instante en múltiples terminales sin recargar ni hacer sondeo.
3. **📋 Historial de Auditoría Legal (Audit Log):**  
   Registro inmutable y detallado de cada radicación, modificación, aprobación o rechazo de predios y documentos.
4. **📸 Exportación del Plano a Imagen PNG:**  
   Botón en el visor cartográfico que permite exportar la vista satelital y el polígono levantado a una imagen PNG de alta resolución.
5. **🛡️ Protección de Bienes Públicos de Sincelejo:**  
   11 predios emblemáticos precargados (CC Guacarí, CC Viva, Éxito, Alcaldía, Gobernación, Catedral, Parque Santander, etc.) con bloqueo automático de invasión.
6. **📶 Detección Automática de Conectividad & Sincronización:**  
   Indicador visual en vivo (🟢 En Línea / 🟠 Modo Offline) y sincronización automática en segundo plano cuando se restablece la red.

---

## 🧪 Verificación y Pruebas Unitarias (JUnit 5)

El software cuenta con **41 pruebas automatizadas** de persistencia, geometría, seguridad BCrypt, generación de PDF con QR y sincronización.

Para ejecutar todas las pruebas:
```powershell
.\mvnw.cmd test
```
*(Todas las suites pasan 100% en verde: `BUILD SUCCESS`).*

---

## 👥 Equipo de Desarrollo
* **Cristian Morales** — Arquitecto de Software
* **Andrés Diaz** — Desarrollador Core
* **Leider Barreto** — Desarrollador UI/UX
* **Aldo Ibañez** — Desarrollador de Persistencia
* **José Sánchez** — Analista de Calidad (QA)
