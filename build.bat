@echo off
REM Build every Eclipse plugin under this repo into a JAR (Windows).
REM
REM Each plugin lives in a sibling directory with the standard PDE layout
REM (META-INF\MANIFEST.MF, plugin.xml, src\, optional icons\). Output: one
REM <symbolic-name>_<base>.<timestamp>.jar per plugin in the plugin's own dir.
REM
REM Requires ECLIPSE_HOME to point at an Eclipse install.

setlocal enableextensions enabledelayedexpansion

if "%ECLIPSE_HOME%"=="" (
    echo ECLIPSE_HOME not set. Point it at an Eclipse install, e.g.
    echo   set ECLIPSE_HOME=C:\dev\glist\zbin\glistzbin-win64\eclipse\eclipsecpp
    exit /b 1
)

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "PLUGINS=%ECLIPSE_HOME%\plugins"

set "JAVA_HOME="
for /d %%D in ("%PLUGINS%\org.eclipse.justj.openjdk.hotspot.jre.full.*") do (
    if exist "%%D\jre\bin\javac.exe" set "JAVA_HOME=%%D\jre"
)
if "%JAVA_HOME%"=="" (
    echo No bundled JRE with javac found under %PLUGINS%
    exit /b 1
)

REM Shared compile classpath.
set "CP="
call :addbundle org.eclipse.ui
call :addbundle org.eclipse.ui.ide
call :addbundle org.eclipse.ui.workbench
call :addbundle org.eclipse.core.runtime
call :addbundle org.eclipse.core.resources
call :addbundle org.eclipse.core.jobs
call :addbundle org.eclipse.core.commands
call :addbundle org.eclipse.core.expressions
call :addbundle org.eclipse.debug.core
call :addbundle org.eclipse.jface
call :addbundle org.eclipse.swt
call :addbundle org.eclipse.equinox.common
call :addbundle org.eclipse.osgi

for %%F in ("%PLUGINS%\org.eclipse.swt.win32.win32.x86_64_*.jar" ^
            "%PLUGINS%\org.eclipse.swt.cocoa.macosx.aarch64_*.jar" ^
            "%PLUGINS%\org.eclipse.swt.cocoa.macosx.x86_64_*.jar" ^
            "%PLUGINS%\org.eclipse.swt.gtk.linux.x86_64_*.jar") do (
    if not "%%~F"=="" if exist "%%~F" if "!CP_SWT!"=="" set "CP_SWT=%%~F" & set "CP=!CP!;%%~F"
)

for /f "tokens=2 delims==" %%T in ('wmic os get LocalDateTime /value ^| find "="') do set "TS=%%T"
set "TIMESTAMP=%TS:~0,12%"

set "FOUND_ANY="
for /d %%P in ("%ROOT%\*") do (
    if exist "%%P\META-INF\MANIFEST.MF" (
        set "FOUND_ANY=1"
        call :build_plugin "%%P"
        if errorlevel 1 exit /b 1
    )
)

if "%FOUND_ANY%"=="" (
    echo No plugin directories found under %ROOT%
    exit /b 1
)

echo.
echo Install into Eclipse by copying each *.jar to %%ECLIPSE_HOME%%\plugins\ and
echo appending a matching line to bundles.info, or rely on the zbin builder to
echo wire them in automatically.

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


:build_plugin
set "PLUGIN_DIR=%~1"
set "MANIFEST=%PLUGIN_DIR%\META-INF\MANIFEST.MF"

REM Parse symbolic name.
set "SYM_NAME="
for /f "tokens=2" %%V in ('findstr /b "Bundle-SymbolicName:" "%MANIFEST%"') do (
    for /f "tokens=1 delims=;" %%S in ("%%V") do set "SYM_NAME=%%S"
)
REM Parse base version (x.y.z).
set "BASE_VERSION="
for /f "tokens=2" %%V in ('findstr /b "Bundle-Version:" "%MANIFEST%"') do (
    for /f "tokens=1 delims=." %%A in ("%%V") do (
        for /f "tokens=2 delims=." %%B in ("%%V") do (
            for /f "tokens=3 delims=." %%C in ("%%V") do (
                set "BASE_VERSION=%%A.%%B.%%C"
            )
        )
    )
)

if "%SYM_NAME%"=="" (
    echo   Skipping %PLUGIN_DIR% (no Bundle-SymbolicName)
    exit /b 0
)
if "%BASE_VERSION%"=="" (
    echo   Skipping %PLUGIN_DIR% (no Bundle-Version)
    exit /b 0
)

set "VERSION=%BASE_VERSION%.%TIMESTAMP%"
set "JAR_NAME=%SYM_NAME%_%VERSION%.jar"

echo == Building %SYM_NAME% (%VERSION%) ==

del /q "%PLUGIN_DIR%\%SYM_NAME%_*.jar" 2>nul
if exist "%PLUGIN_DIR%\bin" rmdir /s /q "%PLUGIN_DIR%\bin"
mkdir "%PLUGIN_DIR%\bin"

REM Collect Java sources.
set "SRCS="
for /r "%PLUGIN_DIR%\src" %%F in (*.java) do set "SRCS=!SRCS! "%%F""

"%JAVA_HOME%\bin\javac.exe" --release 21 -cp "%CP%" -d "%PLUGIN_DIR%\bin" %SRCS%
if errorlevel 1 exit /b 1

set "TMP_MANIFEST=%TEMP%\glist-eclipse-plugin-manifest-%SYM_NAME%.mf"
powershell -NoProfile -Command "(Get-Content -Raw '%MANIFEST%') -replace '\d+\.\d+\.\d+\.qualifier','%VERSION%' | Set-Content -NoNewline '%TMP_MANIFEST%'"

set "JAR_CMD="%JAVA_HOME%\bin\jar.exe" cfm "%PLUGIN_DIR%\%JAR_NAME%" "%TMP_MANIFEST%" -C "%PLUGIN_DIR%\bin" ."
if exist "%PLUGIN_DIR%\plugin.xml" set "JAR_CMD=%JAR_CMD% -C "%PLUGIN_DIR%" plugin.xml"
if exist "%PLUGIN_DIR%\icons"      set "JAR_CMD=%JAR_CMD% -C "%PLUGIN_DIR%" icons"
if exist "%PLUGIN_DIR%\OSGI-INF"   set "JAR_CMD=%JAR_CMD% -C "%PLUGIN_DIR%" OSGI-INF"

call %JAR_CMD%
if errorlevel 1 exit /b 1
del "%TMP_MANIFEST%"

echo   -^> %PLUGIN_DIR%\%JAR_NAME%
exit /b 0
