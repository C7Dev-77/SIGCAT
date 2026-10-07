# Matriz de Casos de Prueba — Registro y Aprobación de Predios (SIGCAT)

Responsable: José Sánchez (QA)

## Módulo: Dibujo y Cálculo de Predio

| ID | Caso de prueba | Precondición | Pasos | Resultado esperado | Tipo |
|----|----------------|--------------|-------|---------------------|------|
| CP-13 | Cálculo de área correcto | Ninguna | Dibujar un cuadrado de 10x10 en el Canvas | El área mostrada debe ser 100 px² | Unitaria |
| CP-14 | Cierre de polígono con menos de 3 puntos | Ninguna | Hacer clic solo 2 veces e intentar cerrar | El sistema no permite cerrar el polígono | Funcional |
| CP-15 | Predio sin solapamiento se guarda | No existen predios aprobados que se crucen | Dibujar y cerrar un polígono en zona libre | Mensaje de éxito, predio queda en estado PENDIENTE en la BD | Integración |
| CP-16 | Predio con solapamiento se rechaza del registro | Existe un predio APROBADO en la misma zona | Dibujar un polígono que se cruce con el aprobado | Mensaje de advertencia de solapamiento, el predio NO se guarda en la BD | Integración |
| CP-17 | Predio completamente dentro de otro se detecta | Existe un predio APROBADO grande | Dibujar un polígono pequeño dentro del área del aprobado | Se detecta como solapamiento (no debe pasar desapercibido) | Integración |
| CP-18 | Reiniciar dibujo limpia el estado | Hay puntos dibujados sin cerrar | Clic en "Reiniciar dibujo" | El Canvas se limpia, área vuelve a "-", Canvas queda habilitado | Funcional |
| CP-19 | Ventana no redimensionable durante el dibujo | Está en la pantalla de dibujo | Intentar arrastrar el borde de la ventana | La ventana no cambia de tamaño | Funcional |

## Módulo: Panel del Funcionario (Aprobación)

| ID | Caso de prueba | Precondición | Pasos | Resultado esperado | Tipo |
|----|----------------|--------------|-------|---------------------|------|
| CP-20 | Listar solicitudes pendientes | Existen N predios en estado PENDIENTE | Iniciar sesión como funcionario | La tabla muestra exactamente los N predios pendientes, con nombre de propietario resuelto | Funcional |
| CP-21 | Aprobar una solicitud | Hay al menos 1 predio PENDIENTE | Seleccionar una fila, clic en "Aprobar" | El predio cambia a estado APROBADO en la BD, desaparece de la tabla de pendientes, queda registrado el id del funcionario | Integración |
| CP-22 | Rechazar una solicitud | Hay al menos 1 predio PENDIENTE | Seleccionar una fila, clic en "Rechazar" | El predio cambia a estado RECHAZADO en la BD, desaparece de la tabla de pendientes | Integración |
| CP-23 | Aprobar/Rechazar sin seleccionar fila | Tabla con filas, ninguna seleccionada | Clic en "Aprobar" sin seleccionar nada | Mensaje pidiendo seleccionar una solicitud, no se modifica la BD | Funcional |
| CP-24 | Predio aprobado pasa a afectar futuros registros | Se aprobó un predio en CP-21 | Un nuevo propietario dibuja un polígono que se cruza con el recién aprobado | Se detecta solapamiento (confirma que listarAprobadosConVertices ya lo incluye) | Integración (regresión) |

## Módulo: Persistencia (nivel de datos)

| ID | Caso de prueba | Precondición | Pasos | Resultado esperado | Tipo |
|----|----------------|--------------|-------|---------------------|------|
| CP-25 | Insertar predio con vértices | BD inicializada | PredioDAO.insertarConVertices(predio con 4 vértices) | Retorna un id > 0; los 4 vértices quedan asociados a ese id en la tabla vertices | Unitaria |
| CP-26 | Listar solo predios PENDIENTE | Hay predios en distintos estados | PredioDAO.listarPendientes() | Solo retorna los predios con estado = PENDIENTE | Unitaria |
| CP-27 | Listar solo predios APROBADO con vértices | Hay predios en distintos estados | PredioDAO.listarAprobadosConVertices() | Solo retorna los APROBADO, cada uno con su lista de vértices cargada | Unitaria |
| CP-28 | Actualizar estado de un predio | Existe un predio PENDIENTE | PredioDAO.actualizarEstado(id, APROBADO, idFuncionario) | El predio queda con estado APROBADO y el id_funcionario correcto | Unitaria |