@echo off
setlocal enabledelayedexpansion

REM Licensed to the Apache Software Foundation (ASF) under one or more
REM contributor license agreements.  The ASF licenses this file to You
REM under the Apache License, Version 2.0 (the "License"); you may not
REM use this file except in compliance with the License.
REM You may obtain a copy of the License at
REM
REM     http://www.apache.org/licenses/LICENSE-2.0
REM
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.  For additional information regarding
REM copyright in this work, please see the NOTICE file in the top level
REM directory of this distribution.

echo === Roller Docker Build and Push ===

REM Project name (sanitized)
REM Sanitize: lowercase, replace non-alphanumeric with hyphens, trim hyphens
set "PROJECT_NAME=roller"
set "IMAGE_NAME=roller"

REM Ask for target platform
echo.
echo Choose target platform:
echo   1) AWS ECR
echo   2) Docker Hub
set /p PLATFORM_CHOICE="Enter choice (1 or 2): "

if "!PLATFORM_CHOICE!"=="1" (
    REM AWS ECR
    echo.
    set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
    set /p ECR_REPO="Enter ECR repository name (default: !IMAGE_NAME!): "
    if "!ECR_REPO!"=="" set "ECR_REPO=!IMAGE_NAME!"
    
    REM Get AWS account ID
    aws sts get-caller-identity --query Account --output text --region !AWS_REGION! > "%TEMP%\aws_account_id.txt" 2>nul
    set /p AWS_ACCOUNT_ID=<"%TEMP%\aws_account_id.txt"
    del "%TEMP%\aws_account_id.txt" 2>nul
    set "REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
    
    echo.
    echo === Logging in to Amazon ECR ===
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (echo ECR login failed & exit /b 1)
    
    echo.
    echo === Creating ECR repository if it doesn't exist ===
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )
    
    set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!"
) else if "!PLATFORM_CHOICE!"=="2" (
    REM Docker Hub
    echo.
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password: "
    
    echo.
    echo === Logging in to Docker Hub ===
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (echo Docker Hub login failed & exit /b 1)
    
    set "FULL_IMAGE_NAME=docker.io/!DOCKER_USERNAME!/!IMAGE_NAME!"
) else (
    echo ERROR: Invalid choice. Please run again with 1 or 2.
    exit /b 1
)

REM Ask for image tag
echo.
set /p IMAGE_TAG="Enter image tag (default: latest): "
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

set "FULL_IMAGE_NAME=!FULL_IMAGE_NAME!:!IMAGE_TAG!"

echo.
echo === Building Docker image ===
echo Image: !FULL_IMAGE_NAME!
docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (echo Docker build failed & exit /b 1)

echo.
echo === Pushing Docker image ===
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (echo Docker push failed & exit /b 1)

echo.
echo === Build and push complete ===
echo Image: !FULL_IMAGE_NAME!

endlocal
