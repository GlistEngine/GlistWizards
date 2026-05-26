@echo off
REM Build com.aitial.glist.wizards plugin JAR (Windows).
REM
REM Requires ECLIPSE_HOME to point at an Eclipse install (the directory
REM containing plugins\, configuration\, eclipse.ini). Uses Eclipse's
REM bundled JRE + plugin JARs for the compile classpath, so no extra Java
REM setup is needed beyond the Eclipse install.

setlocal enableextensions enabledelayedexpansion

if "%ECLIPSE_HOME%"=="" (
    echo ECLIPSE_HOME not set. Point it at an Eclipse install, e.g.
    echo   set ECLIPSE_HOME=C:\dev\glist\zbin\glistzbin-win64\eclipse\eclipsecpp
    exit /b 1
)

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "PLUGINS=%ECLIPSE_HOME%\plugins"

REM Find the bundled JRE folder.
set "JAVA_HOME="
for /d %%D in ("%PLUGINS%\org.eclipse.justj.openjdk.hotspot.jre.full.*") do (
    if exist "%%D\jre\bin\javac.exe" set "JAVA_HOME=%%D\jre"
)
if "%JAVA_HOME%"=="" (
    echo No bundled JRE with javac found under %PLUGINS%
    exit /b 1
)

REM Build the Required-Bundle classpath by globbing for the latest of each.
set "CP="
call :addbundle org.eclipse.ui
call :addbundle org.eclipse.ui.ide
call :addbundle org.eclipse.ui.workbench
call :addbundle org.eclipse.core.runtime
call :addbundle org.eclipse.core.resources
call :addbundle org.eclipse.core.jobs
call :addbundle org.eclipse.debug.core
call :addbundle org.eclipse.jface
call :addbundle org.eclipse.swt
call :addbundle org.eclipse.equinox.common
call :addbundle org.eclipse.osgi

REM Pick a platform-specific SWT (any will do for compile-time refs).
for %%F in ("%PLUGINS%\org.eclipse.swt.win32.win32.x86_64_*.jar" ^
            "%PLUGINS%\org.eclipse.swt.cocoa.macosx.aarch64_*.jar" ^
            "%PLUGINS%\org.eclipse.swt.cocoa.macosx.x86_64_*.jar" ^
            "%PLUGINS%\org.eclipse.swt.gtk.linux.x86_64_*.jar") do (
    if not "%%~F"=="" if exist "%%~F" if "!CP_SWT!"=="" set "CP_SWT=%%~F" & set "CP=!CP!;%%~F"
)

REM Read the base version (x.y.z) from MANIFEST.MF.
set "BASE_VERSION="
for /f "tokens=2" %%V in ('findstr /b "Bundle-Version:" "%ROOT%\META-INF\MANIFEST.MF"') do (
    for /f "tokens=1 delims=." %%A in ("%%V") do (
        for /f "tokens=2 delims=." %%B in ("%%V") do (
            for /f "tokens=3 delims=." %%C in ("%%V") do (
                set "BASE_VERSION=%%A.%%B.%%C"
            )
        )
    )
)
if "%BASE_VERSION%"=="" (
    echo Could not parse Bundle-Version from MANIFEST.MF
    exit /b 1
)

REM Stamp a fresh version timestamp.
for /f "tokens=2 delims==" %%T in ('wmic os get LocalDateTime /value ^| find "="') do set "TS=%%T"
set "VERSION=%BASE_VERSION%.%TS:~0,12%"
set "JAR_NAME=com.aitial.glist.wizards_%VERSION%.jar"

if exist "%ROOT%\bin" rmdir /s /q "%ROOT%\bin"
mkdir "%ROOT%\bin"

"%JAVA_HOME%\bin\javac.exe" --release 21 -cp "%CP%" -d "%ROOT%\bin" "%ROOT%\src\com\aitial\glist\wizards\*.java"
if errorlevel 1 exit /b 1

REM Stamp version into manifest before packing. Regex matches any "<base>.qualifier".
set "TMP_MANIFEST=%TEMP%\glist-wizards-manifest.mf"
powershell -NoProfile -Command "(Get-Content -Raw '%ROOT%\META-INF\MANIFEST.MF') -replace '\d+\.\d+\.\d+\.qualifier','%VERSION%' | Set-Content -NoNewline '%TMP_MANIFEST%'"

pushd "%ROOT%"
"%JAVA_HOME%\bin\jar.exe" cfm "%ROOT%\%JAR_NAME%" "%TMP_MANIFEST%" -C bin com -C . plugin.xml -C . icons
popd
del "%TMP_MANIFEST%"

echo.
echo Built %ROOT%\%JAR_NAME%
echo.
echo Install into Eclipse by:
echo   1. Copy the JAR to %%ECLIPSE_HOME%%\plugins\
echo   2. Append this line to %%ECLIPSE_HOME%%\configuration\org.eclipse.equinox.simpleconfigurator\bundles.info:
echo      com.aitial.glist.wizards,%VERSION%,plugins/%JAR_NAME%,4,false
echo      (remove any older com.aitial.glist.wizards line first)

exit /b 0

:addbundle
set "NAME=%~1"
set "FOUND="
for /f %%F in ('dir /b /od "%PLUGINS%\%NAME%_*.jar" 2^>nul') do set "FOUND=%%F"
if not "%FOUND%"=="" (
    if "%CP%"=="" (
        set "CP=%PLUGINS%\%FOUND%"
    ) else (
        set "CP=%CP%;%PLUGINS%\%FOUND%"
    )
)
exit /b 0
