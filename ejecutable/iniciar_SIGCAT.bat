@echo off
setlocal
title SIGCAT - Sincelejo, Sucre
echo Iniciando SIGCAT v2.0...
start "" javaw -jar "%~dp0SIGCAT.jar"
if %errorlevel% neq 0 (
    java -jar "%~dp0SIGCAT.jar"
)
