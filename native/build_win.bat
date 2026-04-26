@echo off
REM Build C KD-tree native library for Windows
REM Produces: lib\pflownn.dll
REM Requires: Visual Studio Build Tools (cl.exe in PATH)
cd /d "%~dp0"

if not defined JAVA_HOME (
    echo ERROR: JAVA_HOME not set. Install JDK 21.
    exit /b 1
)

echo [BUILD] JAVA_HOME: %JAVA_HOME%
echo [BUILD] Compiling C KD-tree...

if not exist lib mkdir lib

cl /O2 /LD /W3 ^
    /I"%JAVA_HOME%\include" ^
    /I"%JAVA_HOME%\include\win32" ^
    src\kdtree.c src\jni_bridge.c ^
    /Fe:lib\pflownn.dll

if %errorlevel%==0 (
    echo [BUILD] Success: lib\pflownn.dll
    del *.obj 2>nul
) else (
    echo [BUILD] FAILED
    exit /b 1
)
