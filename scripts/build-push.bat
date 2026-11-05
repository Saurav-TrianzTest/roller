@echo off
setlocal enabledelayedexpansion

REM build-push.bat - Build and push the Roller container image
REM Run from the repository root

set "PROJECT_NAME=roller"
set "IMAGE_NAME=roller"

echo === Building and pushing Roller container image ===
echo Project name: %PROJECT_NAME%
echo Image name: %IMAGE_NAME%

echo.
echo Choose target platform:
echo 1. AWS ECR
echo 2. Docker Hub
set /p PLATFORM_CHOICE="Enter choice (1 or 2): "

if "%PLATFORM_CHOICE%"=="1" (
    set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
    set /p REGISTRY_URL="Enter AWS ECR registry URL (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com): "
    set /p ECR_REPO="Enter ECR repository name (e.g., roller): "
    set /p IMAGE_TAG="Enter image tag (or press Enter for 'latest'): "

    if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

    REM Sanitize tag with PowerShell
    for /f "delims=" %%i in ('powershell -command "^'!IMAGE_TAG!' -replace '[^a-z0-9.\-]', '-'"') do set "IMAGE_TAG=%%i"

    set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"

    echo.
    echo === Logging in to AWS ECR ===
    call aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ECR login failed
        exit /b 1
    )

    echo.
    echo === Creating ECR repository if it doesn't exist ===
    call aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository: !ECR_REPO!
        call aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )

    echo.
    echo === Building Docker image ===
    docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .

    echo.
    echo === Pushing Docker image ===
    docker push "!FULL_IMAGE_NAME!"

    echo.
    echo === Build and push complete ===
    echo Image: !FULL_IMAGE_NAME!

) else if "%PLATFORM_CHOICE%"=="2" (
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password: "
    set /p IMAGE_TAG="Enter image tag (or press Enter for 'latest'): "

    if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

    REM Sanitize tag with PowerShell
    for /f "delims=" %%i in ('powershell -command "^'!IMAGE_TAG!' -replace '[^a-z0-9.\-]', '-'"') do set "IMAGE_TAG=%%i"

    set "FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!"

    echo.
    echo === Logging in to Docker Hub ===
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo Docker Hub login failed
        exit /b 1
    )

    echo.
    echo === Building Docker image ===
    docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .

    echo.
    echo === Pushing Docker image ===
    docker push "!FULL_IMAGE_NAME!"

    echo.
    echo === Build and push complete ===
    echo Image: !FULL_IMAGE_NAME!

) else (
    echo ERROR: Invalid choice. Exiting.
    exit /b 1
)

endlocal
