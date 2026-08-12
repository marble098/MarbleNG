@echo off
where gradle >nul 2>nul || (echo Install Gradle 9.5.1 or use GitHub Actions. & exit /b 1)
gradle %*
