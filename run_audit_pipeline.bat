@echo off
REM Post-audit full-pipeline smoke test.
REM Truck EXPANDED -> Taxi Tokyo+Osaka -> Full-geometry trajectories.
REM Uses bin_fresh (Java 21 compiled with audit edits).

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

set "NET=..\data\network"
set "ALL_PREFS=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47"
set "TOKYO_PREFS=8,9,10,11,12,13,14"
set "OSAKA_PREFS=24,25,26,27,28,29,30"
set "MAX_RC=9"

REM timestamp for log dir (YYYYMMDD_HHMMSS)
for /f "tokens=1-4 delims=/-. " %%a in ("%date%") do set "D=%%d%%b%%c"
set "D=%D: =0%"
for /f "tokens=1-3 delims=:. " %%a in ("%time%") do set "T=%%a%%b%%c"
set "T=%T: =0%"
set "LOG_DIR=logs\audit_pipeline_%D%_%T%"
mkdir "%LOG_DIR%" 2>nul
echo Logs -^> %LOG_DIR%

echo.
echo ======== STEP 1/6: Truck EXPANDED sim ========
echo %date% %time%
"%JAVA%" -Xmx16G -cp "%CP%" truck.sim.TruckSimulation config\truck\truck_config_expanded.properties > "%LOG_DIR%\01_truck.log" 2>&1
if errorlevel 1 ( echo STEP 1 FAILED - see %LOG_DIR%\01_truck.log & exit /b 1 )
for /f "delims=" %%i in ('dir /b /ad /o-d "output\trips\truck\run_*"') do ( set "TRUCK_RUN=%%i" & goto :have_truck )
:have_truck
set "TRUCK_CSV=output\trips\truck\%TRUCK_RUN%\trips_pseudo_pflow.csv"
echo [driver] truck trips: %TRUCK_CSV%

echo.
echo ======== STEP 2/6: Taxi Tokyo sim ========
echo %date% %time%
"%JAVA%" -Xmx6G -cp "%CP%" taxi.sim.TaxiSimulation config\taxi\tokyo\taxi_config.properties > "%LOG_DIR%\02_taxi_tokyo.log" 2>&1
if errorlevel 1 ( echo STEP 2 FAILED - see %LOG_DIR%\02_taxi_tokyo.log & exit /b 1 )
for /f "delims=" %%i in ('dir /b /ad /o-d "output\trips\taxi\tokyo\run_*"') do ( set "TOKYO_RUN=%%i" & goto :have_tokyo )
:have_tokyo
set "TOKYO_CSV=output\trips\taxi\tokyo\%TOKYO_RUN%\trips_pseudo_pflow.csv"
echo [driver] tokyo trips: %TOKYO_CSV%

echo.
echo ======== STEP 3/6: Taxi Osaka sim ========
echo %date% %time%
"%JAVA%" -Xmx6G -cp "%CP%" taxi.sim.TaxiSimulation config\taxi\osaka\taxi_config.properties > "%LOG_DIR%\03_taxi_osaka.log" 2>&1
if errorlevel 1 ( echo STEP 3 FAILED - see %LOG_DIR%\03_taxi_osaka.log & exit /b 1 )
for /f "delims=" %%i in ('dir /b /ad /o-d "output\trips\taxi\osaka\run_*"') do ( set "OSAKA_RUN=%%i" & goto :have_osaka )
:have_osaka
set "OSAKA_CSV=output\trips\taxi\osaka\%OSAKA_RUN%\trips_pseudo_pflow.csv"
echo [driver] osaka trips: %OSAKA_CSV%

echo.
echo ======== STEP 4/6: Truck trajectory FULL (all 47 prefs) ========
echo %date% %time%
"%JAVA%" -Xmx56G -XX:+UseG1GC -cp "%CP%" traj.TrajectoryMain truck "%TRUCK_CSV%" "%NET%" "output\trajectory\truck" "%ALL_PREFS%" %MAX_RC% full > "%LOG_DIR%\04_traj_truck.log" 2>&1
if errorlevel 1 ( echo STEP 4 FAILED - see %LOG_DIR%\04_traj_truck.log & exit /b 1 )

echo.
echo ======== STEP 5/6: Tokyo taxi trajectory FULL (prefs 8-14) ========
echo %date% %time%
"%JAVA%" -Xmx56G -XX:+UseG1GC -cp "%CP%" traj.TrajectoryMain taxi "%TOKYO_CSV%" "%NET%" "output\trajectory\taxi\tokyo" "%TOKYO_PREFS%" %MAX_RC% full > "%LOG_DIR%\05_traj_tokyo.log" 2>&1
if errorlevel 1 ( echo STEP 5 FAILED - see %LOG_DIR%\05_traj_tokyo.log & exit /b 1 )

echo.
echo ======== STEP 6/6: Osaka taxi trajectory FULL (prefs 24-30) ========
echo %date% %time%
"%JAVA%" -Xmx56G -XX:+UseG1GC -cp "%CP%" traj.TrajectoryMain taxi "%OSAKA_CSV%" "%NET%" "output\trajectory\taxi\osaka" "%OSAKA_PREFS%" %MAX_RC% full > "%LOG_DIR%\06_traj_osaka.log" 2>&1
if errorlevel 1 ( echo STEP 6 FAILED - see %LOG_DIR%\06_traj_osaka.log & exit /b 1 )

echo.
echo ======== PIPELINE DONE ========
echo %date% %time%
echo Logs in: %LOG_DIR%
endlocal
