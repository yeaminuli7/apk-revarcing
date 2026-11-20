@echo off
rem Wrapper so the setup can be started from plain cmd.exe.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0windows-setup.ps1" %*
