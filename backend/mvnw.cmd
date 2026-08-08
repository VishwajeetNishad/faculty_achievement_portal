@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion

set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_PROPERTIES="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

if exist %WRAPPER_JAR% (
    goto run
)

echo Downloading Maven Wrapper...
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar', '%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar')"

:run
java -jar %WRAPPER_JAR% %*
