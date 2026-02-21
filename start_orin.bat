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

where python >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Python not found. Please install Python 3.10+ and add it to PATH.
    pause
    exit /b
)

echo Starting Backend (Spring Boot)...
start "ORIN Backend" cmd /c "cd orin-backend && mvn spring-boot:run"

echo Starting AI Engine (Python)...
start "ORIN AI Engine" cmd /c "cd orin-ai-engine && venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8000"

echo Starting Frontend (Vue)...
start "ORIN Frontend" cmd /c "cd orin-frontend && npm install && npm run dev"

echo ===================================================
echo ORIN started!
echo Frontend: http://localhost:5173
echo Backend: http://localhost:8080
echo AI Engine: http://localhost:8000
echo ===================================================

pause
