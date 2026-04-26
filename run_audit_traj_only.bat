@echo off
REM Trajectory-only replay for audit pipeline. Sims already produced outputs
REM under %PFLOW_HOME%/output/trips/{truck,taxi/tokyo,taxi/osaka}/run_20260422_*.

setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "JAVA=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\java.exe"
set "M2=C:\Users\north\.m2\repository"

set "CP=bin_fresh"
set "CP=%CP%;lib\pflowlib.jar"
set "CP=%CP%;%M2%\org\locationtech\jts\jts-core\1.16.0\jts-core-1.16.0.jar"
set "CP=%CP%;%M2%\com\vividsolutions\jts\1.13\jts-1.13.jar"
set "CP=%CP%;%M2%\org\geotools\gt-api\20.1\gt-api-20.1.jar"
set "CP=%CP%;%M2%\org\geotools\gt-data\20.1\gt-data-20.1.jar"
set "CP=%CP%;%M2%\org\geotools\gt-main\20.1\gt-main-20.1.jar"
set "CP=%CP%;%M2%\org\geotools\gt-metadata\20.1\gt-metadata-20.1.jar"
set "CP=%CP%;%M2%\org\geotools\gt-opengis\20.1\gt-opengis-20.1.jar"
set "CP=%CP%;%M2%\org\geotools\gt-referencing\20.1\gt-referencing-20.1.jar"
set "CP=%CP%;%M2%\org\geotools\gt-shapefile\20.1\gt-shapefile-20.1.jar"
set "CP=%CP%;%M2%\org\geotools\gt-epsg-hsql\20.1\gt-epsg-hsql-20.1.jar"
set "CP=%CP%;%M2%\org\hsqldb\hsqldb\2.4.1\hsqldb-2.4.1.jar"
set "CP=%CP%;%M2%\commons-pool\commons-pool\1.5.4\commons-pool-1.5.4.jar"
set "CP=%CP%;%M2%\commons-lang\commons-lang\2.6\commons-lang-2.6.jar"
set "CP=%CP%;%M2%\javax\measure\unit-api\1.0\unit-api-1.0.jar"
set "CP=%CP%;%M2%\si\uom\si-quantity\0.7.1\si-quantity-0.7.1.jar"
set "CP=%CP%;%M2%\si\uom\si-units-java8\0.7.1\si-units-java8-0.7.1.jar"
set "CP=%CP%;%M2%\systems\uom\systems-common-java8\0.7.2\systems-common-java8-0.7.2.jar"
set "CP=%CP%;%M2%\tec\uom\uom-se\1.0.8\uom-se-1.0.8.jar"
set "CP=%CP%;%M2%\tec\uom\lib\uom-lib-common\1.0.2\uom-lib-common-1.0.2.jar"
set "CP=%CP%;%M2%\net\java\dev\jsr-275\jsr-275\1.0-beta-2\jsr-275-1.0-beta-2.jar"
set "CP=%CP%;%M2%\org\ejml\ejml-core\0.34\ejml-core-0.34.jar"
set "CP=%CP%;%M2%\org\ejml\ejml-ddense\0.34\ejml-ddense-0.34.jar"

set "PFLOW_HOME=H:\Dropbox\PFLOW"
set "NET=%PFLOW_HOME%\data\network"
set "TRUCK_CSV=%PFLOW_HOME%\output\trips\truck\run_20260422_215727\trips_pseudo_pflow.csv"
set "TOKYO_CSV=%PFLOW_HOME%\output\trips\taxi\tokyo\run_20260422_215819\trips_pseudo_pflow.csv"
set "OSAKA_CSV=%PFLOW_HOME%\output\trips\taxi\osaka\run_20260422_215846\trips_pseudo_pflow.csv"
set "TRUCK_OUT=D:\trajectory\truck"
set "TOKYO_OUT=%PFLOW_HOME%\output\trajectory\taxi\tokyo"
set "OSAKA_OUT=%PFLOW_HOME%\output\trajectory\taxi\osaka"

set "ALL_PREFS=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47"
set "TOKYO_PREFS=8,9,10,11,12,13,14"
set "OSAKA_PREFS=24,25,26,27,28,29,30"
set "MAX_RC=9"

for /f "tokens=1-4 delims=/-. " %%a in ("%date%") do set "D=%%d%%b%%c"
set "D=%D: =0%"
for /f "tokens=1-3 delims=:. " %%a in ("%time%") do set "T=%%a%%b%%c"
set "T=%T: =0%"
set "LOG_DIR=logs\audit_traj_%D%_%T%"
mkdir "%LOG_DIR%" 2>nul
echo Logs -^> %LOG_DIR%

echo.
echo ======== STEP 4/6: Truck trajectory FULL (all 47 prefs) ========
echo %date% %time%
"%JAVA%" -Xmx56G -XX:+UseG1GC -cp "%CP%" traj.TrajectoryMain truck "%TRUCK_CSV%" "%NET%" "%TRUCK_OUT%" "%ALL_PREFS%" %MAX_RC% full > "%LOG_DIR%\04_traj_truck.log" 2>&1
if errorlevel 1 ( echo STEP 4 FAILED - see %LOG_DIR%\04_traj_truck.log & exit /b 1 )

echo.
echo ======== STEP 5/6: Tokyo taxi trajectory FULL (prefs 8-14) ========
echo %date% %time%
"%JAVA%" -Xmx56G -XX:+UseG1GC -cp "%CP%" traj.TrajectoryMain taxi "%TOKYO_CSV%" "%NET%" "%TOKYO_OUT%" "%TOKYO_PREFS%" %MAX_RC% full > "%LOG_DIR%\05_traj_tokyo.log" 2>&1
if errorlevel 1 ( echo STEP 5 FAILED - see %LOG_DIR%\05_traj_tokyo.log & exit /b 1 )

echo.
echo ======== STEP 6/6: Osaka taxi trajectory FULL (prefs 24-30) ========
echo %date% %time%
"%JAVA%" -Xmx56G -XX:+UseG1GC -cp "%CP%" traj.TrajectoryMain taxi "%OSAKA_CSV%" "%NET%" "%OSAKA_OUT%" "%OSAKA_PREFS%" %MAX_RC% full > "%LOG_DIR%\06_traj_osaka.log" 2>&1
if errorlevel 1 ( echo STEP 6 FAILED - see %LOG_DIR%\06_traj_osaka.log & exit /b 1 )

echo.
echo ======== TRAJECTORY PIPELINE DONE ========
echo %date% %time%
echo Logs in: %LOG_DIR%
endlocal
