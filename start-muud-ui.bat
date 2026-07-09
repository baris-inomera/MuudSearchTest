@echo off
title Muud Search UI
cd /d "%~dp0"

echo Muud Search UI baslatiliyor...
echo.

"C:\Users\inomera\.jdks\ms-17.0.19\bin\java.exe" @startup.args web.WebApp

echo.
echo Uygulama kapandi.
pause
