@echo off
setlocal enabledelayedexpansion
rem Loads backend\.env into this window's environment, then starts the app.
rem "tokens=1,*" keeps everything after the first "=" (so values may contain "=").
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
    set "%%A=%%B"
)
mvn spring-boot:run
