# 🗺️ SIGCAT — Sistema de Registro Gráfico de Predios y Detección Automática de Solapamiento Catastral

**Institución:** Universidad Antonio José de Sucre (UNIAJS)  
**Programa:** Ingeniería de Sistemas | **Asignatura:** Ingeniería de Software  
**Docente:** Ing. Juan Manuel Palacio  
**Ubicación Territorial:** Municipio de Sincelejo, Sucre, Colombia (Código DANE: `70001`)

---

## 👥 Equipo de Desarrollo y Roles

| Miembro | Rol | Responsabilidades Principales |
| ------- | --- | ----------------------------- |
| **Cristian Morales** | Arquitecto de Software | Arquitectura del sistema, concurrencia multi-PC, seguridad BCrypt, ViewNavigator y empaquetado Fat JAR |
| **Andrés Diaz** | Desarrollador Core | Motor topológico, cálculo de Shoelace en metros reales ($m^2$), validación de solapamiento y resolución de conflictos |
| **Leider Barreto** | Desarrollador UI/UX | Vistas FXML, diseño visual en CSS, integración del motor OpenStreetMap en Canvas y experiencia de usuario |
| **Aldo Ibañez** | Desarrollador de Persistencia | Esquema relacional SQLite, transacciones ACID con `PredioDAO`, borrado en cascada y consultas de malla |
| **José Sánchez** | Analista de Calidad (QA) | Automatización de pruebas unitarias e integración con JUnit 5 (26 pruebas operativas al 100%) |

---

## 🎯 Visión y Alcance del Sistema

**SIGCAT** es una plataforma de información catastral multipropósito diseñada para la gestión territorial urbana en Sincelejo, Sucre. Permite a los ciudadanos radicar levantamientos de predios de forma georreferenciada sobre cartografía real, y a los funcionarios catastrales auditar, validar topológicamente y emitir dictámenes técnicos sobre las solicitudes en tiempo real.

> ☕ **Restricción Técnica Innegociable — 100% Java Nativo:**  
> El sistema está desarrollado estrictamente en **Java 17 LTS y JavaFX 21**. No utiliza tecnologías web ni navegadores embebidos (cero HTML, CSS web o WebView). Todo el motor cartográfico, proyección de coordenadas, renderizado de teselas y validación geométrica se ejecutan en código Java puro compilado.

---

## 🚀 Módulos y Capacidades del Sistema

### 1. 🔐 Seguridad y Registro Autónomo
- **Autenticación Basada en Roles:** Separación estricta de interfaces para **Propietario** y **Funcionario**.
- **Criptografía BCrypt (RNF-01):** Almacenamiento seguro de contraseñas mediante hash BCrypt con factor de costo 12 y salting dinámico.
- **Registro Autónomo:** Formulario de registro de nuevos propietarios con validación de documento único, verificación de contraseñas y redirección automática con retroalimentación inmediata.

### 2. 🏠 Portal del Propietario ("Mis Predios")
- **Dashboard de Control:** Cuatro tarjetas métricas (KPIs) en tiempo real: *Total Radicados*, *Aprobados*, *En Revisión (Pendientes)* y *Rechazados*.
- **Tabla Catastral Dinámica:** Visualización con semáforo de colores por estado y dictámenes técnicos emitidos por los funcionarios.
- **Edición y Eliminación de Solicitudes:** Funcionalidad para actualizar datos del predio (nombre, dirección, uso de suelo) o retirar solicitudes no aprobadas con confirmación modal.
- **Inspección en Mapa con Doble Clic:** Botón *"🗺️ Ver en Mapa"* y soporte de doble clic en cualquier fila para ubicar inmediatamente el predio sobre la cartografía de Sincelejo.
- **Sincronización Silenciosa:** Sondeo periódico en segundo plano que actualiza el estado de las solicitudes en vivo sin parpadeos visuales.

### 3. 🏛️ Consola de Auditoría del Funcionario
- **Bandeja de Entrada Multicriterio:** Filtrado reactivo e instantáneo (`TODAS LAS SOLICITUDES`, `PENDIENTE`, `APROBADO`, `RECHAZADO`).
- **Validación Topológica Previa a la Aprobación:** Bloqueo preventivo automático si el lote a aprobar invade o solapa un predio ya incorporado a la malla catastral oficial de Sincelejo.
- **Resolución Automática de Conflictos Catastrales:** Si se aprueba una solicitud legítima, el sistema detecta y rechaza automáticamente (con dictamen técnico motivado) todas las demás solicitudes pendientes que colisionen sobre ese mismo terreno.
- **Edición y Depuración:** Opciones para rectificar información de expedientes o depurar solicitudes erróneas con borrado en cascada de vértices.
- **Auditoría Cartográfica Visual:** Inspección del lote trazado sobre la malla urbana oficial mediante botón dedicado o doble clic en la tabla.
- **Dictamen Técnico Vinculante:** Emisión de dictamen con observaciones; justificación técnica obligatoria en caso de rechazo y aprobación con un solo clic.
- **Sincronización Multi-PC:** Refresco automático concurrente para auditoría colaborativa entre varias terminales conectadas en red local.

### 4. 🗺️ Motor Cartográfico OpenStreetMap 100% Nativo
- **Renderizado Directo en Canvas:** Descarga asíncrona multihilo de teselas OpenStreetMap con caché concurrente en memoria (`OsmTileManager`).
- **Navegación Pan & Zoom:** Desplazamiento panorámico fluido (*pan*) arrastrando con el botón principal del mouse y acercamiento/alejamiento (*zoom*) con la rueda de desplazamiento (*scroll*).
- **Georreferenciación Permanente (WGS84):** Los vértices se capturan y persisten en coordenadas geográficas reales `[longitud, latitud]`. Al mover o ampliar el mapa, los polígonos permanecen matemáticamente anclados a sus calles y esquinas reales.
- **Acceso Rápido a Sectores de Sincelejo:** Selector de sectores urbanos preconfigurados:
  - *Centro Histórico (Parque Santander)*
  - *Sector Norte (Universidad de Sucre / Puerta Roja)*
  - *Sector Venecia / Las Mercedes*
  - *Sector Majagual / Pozo de Majagual*
  - *Vista General Urbana de Sincelejo*
- **Lienzo Adaptativo:** Redimensionamiento automático del lienzo al maximizar o ajustar la ventana.

### 5. 📐 Motor Topológico y Métricas Geométricas
- **Cálculo Métrico en Superficie Real ($m^2$ y $m$):** Algoritmo de Gauss / Shoelace con proyección equirectangular local adaptada a la latitud de Sincelejo ($\approx 109.8 \text{ km/grado lon}$ y $111.3 \text{ km/grado lat}$), garantizando áreas y perímetros en metros reales.
- **Validación de Autointersecciones:** Detección en vivo de polígonos complejos o con cruce de aristas antes de permitir el envío.
- **Detección Preventiva de Solapamientos:** Análisis espacial (Bounding Box + JTS Ray-Casting) contra los predios aprobados en la malla catastral oficial.

### 6. 💾 Persistencia Concurrente SQLite
- **Arquitectura Transaccional ACID:** Inserción atómica de predios y sus lotes de vértices mediante `PredioDAO` y `VerticeDAO`.
- **Soporte de Red Multi-PC:** Base de datos configurada con `PRAGMA journal_mode = WAL` y `PRAGMA busy_timeout = 5000` para soportar lecturas y escrituras concurrentes desde varios equipos en red local.
- **Configuración Dinámica:** Resolución de la ubicación de la base de datos mediante la propiedad JVM `-Dsigcat.db=...` o variable de entorno `SIGCAT_DB`.

---

## 🔑 Credenciales Oficiales para Pruebas / Demostración

La base de datos inicializa automáticamente los siguientes usuarios de demostración:

| Rol | Documento / Cédula | Contraseña | Vista Inicial |
| --- | ------------------ | ---------- | ------------- |
| **Funcionario Catastral** | `1001` | `123` | Consola de Auditoría y Dictamen Técnico |
| **Propietaria Catastral** | `2002` | `123` | Portal "Mis Predios" y Radicación de Lotes |

> 💡 **Nuevo Usuario:** También es posible registrar un nuevo propietario en cualquier momento pulsando en *"¿No tienes cuenta? Regístrate aquí"* en la pantalla de inicio.

---

## 📁 Estructura del Proyecto

```
SIGCAT/
├── pom.xml                                   # Configuración de dependencias Maven y Maven Shade Plugin
├── sigcat.db                                 # Base de datos relacional SQLite (concurrente WAL)
└── src/
    ├── main/
    │   ├── java/com/sigcat/
    │   │   ├── app/
    │   │   │   ├── App.java                  # Punto de inicio JavaFX con ventana maximizada
    │   │   │   └── MainLauncher.java         # Entrada universal para ejecución del Fat JAR
    │   │   ├── controllers/
    │   │   │   ├── LoginController.java      # Inicio de sesión y navegación por rol
    │   │   │   ├── RegistroController.java   # Autoregistro de propietarios con BCrypt
    │   │   │   ├── MenuPropietarioController.java # Dashboard del propietario, CRUD e historial
    │   │   │   ├── MenuFuncionarioController.java # Consola de auditoría, dictamen técnico y resolución
    │   │   │   ├── DibujoController.java     # Motor de dibujo, visor OSM y validación en vivo
    │   │   │   └── SolicitudRow.java         # DTO para filas de solicitud
    │   │   ├── dao/
    │   │   │   ├── UsuarioDAO.java           # Acceso a datos de usuarios y autenticación
    │   │   │   ├── PredioDAO.java            # CRUD transaccional de predios y malla catastral
    │   │   │   └── VerticeDAO.java           # Persistencia en lote de vértices
    │   │   ├── geometry/
    │   │   │   ├── ShoelaceCalculator.java   # Algoritmo de Gauss para área, perímetro y centroide
    │   │   │   └── SolapamientoValidator.java# Motor topológico de detección de conflictos
    │   │   ├── models/
    │   │   │   ├── Usuario.java              # Entidad de usuario y rol
    │   │   │   ├── Predio.java               # Entidad de predio catastral
    │   │   │   └── Vertice.java              # Coordenadas geográficas y orden de vértices
    │   │   ├── security/
    │   │   │   └── PasswordUtil.java         # Funciones hash BCrypt factor 12
    │   │   └── utils/
    │   │       ├── DatabaseConnection.java   # Conexión Singleton SQLite con soporte multi-PC
    │   │       ├── DatabaseInitializer.java  # Creación de tablas, triggers y usuarios semilla
    │   │       ├── OsmTileManager.java       # Motor cartográfico OSM nativo Java puro
    │   │       └── ViewNavigator.java        # Gestor de navegación fluida sin saltos de ventana
    │   └── resources/
    │       ├── fxml/
    │       │   ├── login.fxml                # Vista de autenticación institucional
    │       │   ├── registro.fxml             # Vista de registro de propietarios
    │       │   ├── menu-propietario.fxml     # Dashboard y tabla "Mis Predios" (con editar/eliminar)
    │       │   ├── menu-funcionario.fxml     # Bandeja de revisión y dictamen técnico
    │       │   └── dibujo.fxml               # Visor cartográfico interactivo de Sincelejo
    │       └── styles/
    │           └── main.css                  # Hoja de estilos con paleta oscura moderna
    └── test/
        └── java/com/sigcat/
            ├── dao/
            │   ├── PredioDAOTest.java        # Pruebas de persistencia transaccional y filtros (5 tests)
            │   └── UsuarioDAOTest.java       # Pruebas de autenticación y seguridad (10 tests)
            ├── geometry/
            │   ├── ShoelaceCalculatorTest.java # Pruebas de área y perímetro (4 tests)
            │   └── SolapamientoValidatorTest.java # Pruebas de detección topológica (3 tests)
            └── integration/
                └── FlujoAprobacionIntegrationTest.java # Pruebas del flujo integral de negocio (4 tests)
```

---

## 🛠️ Cómo Compilar y Ejecutar

### Prerrequisitos
- **Java Development Kit (JDK):** Versión 17 LTS o superior.
- **Acceso a Internet:** Para la descarga inicial de librerías y teselas cartográficas de OpenStreetMap.

### 1. Ejecutar las Pruebas Automatizadas
Para verificar la suite completa de 26 pruebas unitarias e integrales:
```powershell
.\mvnw.cmd test
```

### 2. Ejecutar la Aplicación en Modo Desarrollo
```powershell
.\mvnw.cmd javafx:run
```

### 3. Empaquetar y Ejecutar el Fat JAR
Para compilar el ejecutable autónomo con todas las dependencias embebidas:
```powershell
.\mvnw.cmd package -DskipTests
java -jar target\SIGCAT-1.0-SNAPSHOT-fat.jar
```

### 4. Configurar Demostración Multi-PC en Red Local
Para conectar múltiples terminales a la misma base de datos compartida:
```powershell
# En la máquina cliente (indicando la ruta compartida de la base de datos):
java -Dsigcat.db="\\IP_DEL_SERVIDOR\carpeta_compartida\sigcat.db" -jar target\SIGCAT-1.0-SNAPSHOT-fat.jar
```

---

## 🧪 Estado de Calidad del Software

- **Suite de Pruebas:** 26 de 26 pruebas unitarias e integrales en verde (`BUILD SUCCESS`).
- **Validación Concurrente:** Pruebas de acceso concurrente y manejo de transacciones en SQLite.
- **Diseño Ergonómico:** Ventana maximizada por defecto y preservación continua del estado visual sin saltos de resolución.
