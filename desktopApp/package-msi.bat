@echo off
set "JAVA_HOME=C:\Program Files\Zulu\zulu-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JAVA not found at %JAVA_HOME%
  exit /b 1
)
java -version
cd /d "%~dp0.."
call gradlew.bat :desktopApp:packageMsi
exit /b %ERRORLEVEL%
