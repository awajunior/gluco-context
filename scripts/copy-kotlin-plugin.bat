@echo off
:: v1.10.26 — copia arquivos Kotlin do plugin de background sync
:: Execute este script após: npx cap sync android

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..
set DEST=%ROOT%\android\app\src\main\java\com\glucocontext\app
set SRC=%ROOT%\android-plugin

echo.
echo === AAPS Assist — Instalando plugin Kotlin de background sync ===
echo.

:: Verificar se a pasta android existe
if not exist "%DEST%" (
    echo ERRO: Pasta Android nao encontrada em:
    echo %DEST%
    echo.
    echo Execute primeiro: npx cap add android ^&^& npx cap sync android
    pause
    exit /b 1
)

:: Copiar AapsBackgroundPlugin.kt
echo Copiando AapsBackgroundPlugin.kt...
copy /Y "%SRC%\AapsBackgroundPlugin.kt" "%DEST%\AapsBackgroundPlugin.kt"
if errorlevel 1 (
    echo ERRO ao copiar AapsBackgroundPlugin.kt
    pause
    exit /b 1
)
echo   OK: AapsBackgroundPlugin.kt copiado

:: Substituir MainActivity.kt
echo Copiando MainActivity.kt...
copy /Y "%SRC%\MainActivity_patch.kt" "%DEST%\MainActivity.kt"
if errorlevel 1 (
    echo ERRO ao copiar MainActivity.kt
    pause
    exit /b 1
)
echo   OK: MainActivity.kt atualizado

echo.
echo === Plugin instalado com sucesso! ===
echo.
echo Proximos passos:
echo   cd android
echo   set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot
echo   set PATH=%%JAVA_HOME%%\bin;%%PATH%%
echo   set ANDROID_HOME=C:\Users\awanderley\AppData\Local\Android\Sdk
echo   gradlew assembleDebug
echo.
