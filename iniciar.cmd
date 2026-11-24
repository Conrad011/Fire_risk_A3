@echo off
rem Inicia o FireRisk: compila se preciso, sobe o servidor e abre o navegador.
setlocal
chcp 65001 >nul
cd /d "%~dp0"

if not defined JAVA_HOME (
    for /d %%D in ("%USERPROFILE%\.jdks\jdk-17*") do set "JAVA_HOME=%%~fD"
)
if defined JAVA_HOME (set "JAVA=%JAVA_HOME%\bin\java.exe") else (set "JAVA=java")
"%JAVA%" -version >nul 2>&1
if errorlevel 1 (
    echo Java 17 ou superior nao encontrado. Instale em https://adoptium.net
    pause
    exit /b 1
)

if not exist .env (
    echo Arquivo .env nao encontrado. Crie-o com a linha NASA_FIRMS_KEY=sua_chave
    pause
    exit /b 1
)

if not exist target\firerisk-1.0.0.jar (
    echo Compilando o projeto...
    call "%~dp0mvnw.cmd" -q -B -DskipTests package
    if errorlevel 1 (
        echo Falha ao compilar.
        pause
        exit /b 1
    )
)

if not defined PORT set "PORT=8080"
echo Abrindo http://localhost:%PORT% ...
start "" /b cmd /c "timeout /t 6 /nobreak >nul & start http://localhost:%PORT%"
"%JAVA%" -Dstdout.encoding=UTF-8 -jar target\firerisk-1.0.0.jar
pause
