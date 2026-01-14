@echo off
setlocal

echo ORIN Launcher for Windows
echo =========================

:: Check specific requirements
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Java not found. Please install Java 17+ and add it to PATH.
    pause
    exit /b
)

where npm >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] npm (Node.js) not found. Please install Node.js.
    pause
    exit /b
)

where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Maven (mvn) not found. Please install Maven and add it to PATH.
    pause
    exit /b
)

echo Starting Backend (Spring Boot)...
start "ORIN Backend" cmd /c "cd orin-backend && mvn spring-boot:run"

echo Starting Frontend (Vue)...
start "ORIN Frontend" cmd /c "cd orin-frontend && npm install && npm run dev"

echo ===================================================
echo ORIN started!
echo Frontend will be available at http://localhost:5173
echo Backend will be available at http://localhost:8080
echo ===================================================

pause
