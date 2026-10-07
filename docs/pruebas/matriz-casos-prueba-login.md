# Matriz de Casos de Prueba — Módulo de Login (SIGCAT)

Responsable: José Sánchez (QA)

| ID | Caso de prueba | Precondición | Pasos | Resultado esperado | Tipo |
|----|----------------|--------------|-------|---------------------|------|
| CP-01 | Login exitoso - Propietario | Usuario "2002"/"123" existe en BD con rol PROPIETARIO | 1. Ingresar documento "2002"<br>2. Ingresar contraseña "123"<br>3. Clic en "Iniciar sesión" | Se autentica correctamente y navega al Panel del Propietario | Funcional |
| CP-02 | Login exitoso - Funcionario | Usuario "1001"/"123" existe en BD con rol FUNCIONARIO | 1. Ingresar documento "1001"<br>2. Ingresar contraseña "123"<br>3. Clic en "Iniciar sesión" | Se autentica correctamente y navega al Panel del Funcionario | Funcional |
| CP-03 | Contraseña incorrecta | Usuario "2002" existe en BD | 1. Ingresar documento "2002"<br>2. Ingresar contraseña incorrecta<br>3. Clic en "Iniciar sesión" | Muestra mensaje de error, no autentica, no navega | Funcional |
| CP-04 | Documento inexistente | Ningún usuario con ese documento | 1. Ingresar documento "99999"<br>2. Ingresar cualquier contraseña<br>3. Clic en "Iniciar sesión" | Muestra mensaje de error, no autentica | Funcional |
| CP-05 | Campo documento vacío | — | 1. Dejar documento vacío<br>2. Ingresar contraseña<br>3. Clic en "Iniciar sesión" | Muestra validación de campo requerido, no intenta autenticar | Funcional |
| CP-06 | Campo contraseña vacío | — | 1. Ingresar documento<br>2. Dejar contraseña vacía<br>3. Clic en "Iniciar sesión" | Muestra validación de campo requerido, no intenta autenticar | Funcional |
| CP-07 | Navegación según rol - Propietario | Login exitoso con rol PROPIETARIO | Completar CP-01 | Carga específicamente menu-propietario.fxml (no el de funcionario) | Funcional |
| CP-08 | Navegación según rol - Funcionario | Login exitoso con rol FUNCIONARIO | Completar CP-02 | Carga específicamente menu-funcionario.fxml (no el de propietario) | Funcional |
| CP-09 | Autenticación a nivel de datos - credenciales válidas | BD inicializada con usuarios semilla | Llamar a UsuarioDAO.autenticar("2002", "123") | Retorna Optional con el Usuario correcto | Unitaria |
| CP-10 | Autenticación a nivel de datos - credenciales inválidas | BD inicializada con usuarios semilla | Llamar a UsuarioDAO.autenticar("2002", "incorrecta") | Retorna Optional.empty() | Unitaria |
| CP-11 | Búsqueda por ID - existente | BD inicializada con usuarios semilla | Llamar a UsuarioDAO.buscarPorId(1) | Retorna Optional con el Usuario correspondiente | Unitaria |
| CP-12 | Búsqueda por ID - inexistente | BD inicializada con usuarios semilla | Llamar a UsuarioDAO.buscarPorId(9999) | Retorna Optional.empty() | Unitaria |