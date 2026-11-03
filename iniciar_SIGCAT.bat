@echo off
setlocal
title SIGCAT - Sistema de Informacion Geografica y Catastral de Sincelejo

echo ===============================================================================
echo   SISTEMA DE INFORMACION GEOGRAFICA Y CATASTRAL (SIGCAT v2.0)
echo   Municipio de Sincelejo, Sucre - Codigo DANE: 70001
echo ===============================================================================
echo.

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] No se encontro Java instalado en el sistema.
    echo Por favor instala Java 17 o superior desde https://adoptium.net/
    echo.
    pause
    exit /b 1
)

if exist "%~dp0ejecutable\SIGCAT.jar" (
    echo [OK] Iniciando SIGCAT desde ejecutable\SIGCAT.jar...
    start "" javaw -jar "%~dp0ejecutable\SIGCAT.jar"
    if %errorlevel% neq 0 (
        echo [AVISO] Fallo javaw, ejecutando con consola java...
        java -jar "%~dp0ejecutable\SIGCAT.jar"
    )
    exit /b 0
)

if exist "%~dp0target\SIGCAT-1.0-SNAPSHOT-fat.jar" (
    echo [OK] Iniciando SIGCAT desde target\SIGCAT-1.0-SNAPSHOT-fat.jar...
    start "" javaw -jar "%~dp0target\SIGCAT-1.0-SNAPSHOT-fat.jar"
    if %errorlevel% neq 0 (
        java -jar "%~dp0target\SIGCAT-1.0-SNAPSHOT-fat.jar"
    )
    exit /b 0
)

echo [AVISO] No se encontro el archivo JAR precompilado.
echo Compilando y ejecutando con Maven Wrapper...
call "%~dp0mvnw.cmd" javafx:run
pause
