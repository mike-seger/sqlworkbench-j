@echo off
certutil -hashfile OpenJDK.sha256.txt sha256 > nul
rem echo level: %errorlevel%

if errorlevel 1 (
  echo The integrity of the file %zipfile% could not be validated - the checksum did not match
)
