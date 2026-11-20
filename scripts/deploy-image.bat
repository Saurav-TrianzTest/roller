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

set "PROJECT_NAME=roller"

echo === Roller EKS Deployment ===

REM Ask for AWS region
set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "

REM Ask for EKS cluster name
set /p CLUSTER_NAME="Enter EKS cluster name: "

REM Ask for image URI
set /p IMAGE_URI="Enter full image URI (registry/repo:tag): "

REM Configure kubectl for the target EKS cluster
echo.
echo === Configuring kubectl for EKS cluster ===
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (echo Failed to configure kubectl & exit /b 1)

REM Verify cluster connectivity
echo.
echo === Verifying cluster connectivity ===
kubectl cluster-info
if !ERRORLEVEL! neq 0 (echo Cluster connectivity check failed & exit /b 1)

REM Collect environment variable values for non-credential variables
echo.
echo === Enter environment variable values ===
echo Press Enter to skip (use placeholder)

set /p DATABASE_JDBC_DRIVERCLASS="Enter value for DATABASE_JDBC_DRIVERCLASS (or press Enter to skip): "
set /p DATABASE_JDBC_CONNECTIONURL="Enter value for DATABASE_JDBC_CONNECTIONURL (or press Enter to skip): "
set /p DATABASE_JDBC_USERNAME="Enter value for DATABASE_JDBC_USERNAME (or press Enter to skip): "
set /p DATABASE_HOST="Enter value for DATABASE_HOST (or press Enter to skip): "
set /p AUTHENTICATION_METHOD="Enter value for AUTHENTICATION_METHOD (or press Enter to skip): "
set /p OIDC_ISSUER_URI="Enter value for OIDC_ISSUER_URI (or press Enter to skip): "
set /p OIDC_CLIENT_ID="Enter value for OIDC_CLIENT_ID (or press Enter to skip): "
set /p OIDC_PROVIDER_ID="Enter value for OIDC_PROVIDER_ID (or press Enter to skip): "
set /p OIDC_CLIENT_NAME="Enter value for OIDC_CLIENT_NAME (or press Enter to skip): "
set /p OIDC_AUTO_PROVISION="Enter value for OIDC_AUTO_PROVISION (or press Enter to skip): "
set /p SESSION_STORE="Enter value for SESSION_STORE (or press Enter to skip): "
set /p UPLOADS_DIR="Enter value for UPLOADS_DIR (or press Enter to skip): "
set /p THEMES_DIR="Enter value for THEMES_DIR (or press Enter to skip): "
set /p SEARCH_INDEX_DIR="Enter value for SEARCH_INDEX_DIR (or press Enter to skip): "
set /p APP_TIMEZONE="Enter value for APP_TIMEZONE (or press Enter to skip): "
set /p TOMCAT_PARSE_FAILED_REASON="Enter value for TOMCAT_PARSE_FAILED_REASON (or press Enter to skip): "
set /p TOMCAT_LOADER_CLASS="Enter value for TOMCAT_LOADER_CLASS (or press Enter to skip): "
set /p BANNEDWORDSLIST_FILE="Enter value for BANNEDWORDSLIST_FILE (or press Enter to skip): "
set /p IPBANLIST_FILE="Enter value for IPBANLIST_FILE (or press Enter to skip): "
set /p DB_SCRIPTS_DIR="Enter value for DB_SCRIPTS_DIR (or press Enter to skip): "
set /p ROLLER_VERSION_PROPS="Enter value for ROLLER_VERSION_PROPS (or press Enter to skip): "
set /p MAIL_HOST="Enter value for MAIL_HOST (or press Enter to skip): "
set /p STATIC_ASSET_CDN="Enter value for STATIC_ASSET_CDN (or press Enter to skip): "

REM Collect credential values (not echoed where the shell can)
echo.
echo === Enter credential values (not echoed) ===
set /p DATABASE_JDBC_PASSWORD="Enter DATABASE_JDBC_PASSWORD (or press Enter to skip): "
echo.
set /p OIDC_CLIENT_SECRET="Enter OIDC_CLIENT_SECRET (or press Enter to skip): "
echo.
set /p MAIL_USERNAME="Enter MAIL_USERNAME (or press Enter to skip): "
echo.
set /p MAIL_PASSWORD="Enter MAIL_PASSWORD (or press Enter to skip): "
echo.

REM Apply namespace
echo.
echo === Applying namespace ===
kubectl apply -f kubernetes/namespace.yaml
if !ERRORLEVEL! neq 0 (echo Failed to apply namespace & exit /b 1)

REM Create secrets for credentials
echo.
echo === Creating Kubernetes Secret ===
set "SECRET_ARGS="
if not "!DATABASE_JDBC_PASSWORD!"=="" (
    set "SECRET_ARGS=!SECRET_ARGS! --from-literal=DATABASE_JDBC_PASSWORD=!DATABASE_JDBC_PASSWORD!"
)
if not "!OIDC_CLIENT_SECRET!"=="" (
    set "SECRET_ARGS=!SECRET_ARGS! --from-literal=OIDC_CLIENT_SECRET=!OIDC_CLIENT_SECRET!"
)
if not "!MAIL_USERNAME!"=="" (
    set "SECRET_ARGS=!SECRET_ARGS! --from-literal=MAIL_USERNAME=!MAIL_USERNAME!"
)
if not "!MAIL_PASSWORD!"=="" (
    set "SECRET_ARGS=!SECRET_ARGS! --from-literal=MAIL_PASSWORD=!MAIL_PASSWORD!"
)

if not "!SECRET_ARGS!"=="" (
    kubectl create secret generic !PROJECT_NAME!-secrets -n !PROJECT_NAME! !SECRET_ARGS! --dry-run=client -o yaml | kubectl apply -f -
    if !ERRORLEVEL! neq 0 (echo Failed to create secret & exit /b 1)
)

REM Replace placeholders in deployment.yaml using PowerShell
echo.
echo === Deploying application ===
powershell -NoProfile -Command ^
    "$content = Get-Content kubernetes/deployment.yaml -Raw; ^
    $content = $content -replace '{{IMAGE_URI}}', '!IMAGE_URI!'; ^
    $content = $content -replace '{{DATABASE_JDBC_DRIVERCLASS}}', '!DATABASE_JDBC_DRIVERCLASS!'; ^
    $content = $content -replace '{{DATABASE_JDBC_CONNECTIONURL}}', '!DATABASE_JDBC_CONNECTIONURL!'; ^
    $content = $content -replace '{{DATABASE_JDBC_USERNAME}}', '!DATABASE_JDBC_USERNAME!'; ^
    $content = $content -replace '{{DATABASE_HOST}}', '!DATABASE_HOST!'; ^
    $content = $content -replace '{{AUTHENTICATION_METHOD}}', '!AUTHENTICATION_METHOD!'; ^
    $content = $content -replace '{{OIDC_ISSUER_URI}}', '!OIDC_ISSUER_URI!'; ^
    $content = $content -replace '{{OIDC_CLIENT_ID}}', '!OIDC_CLIENT_ID!'; ^
    $content = $content -replace '{{OIDC_PROVIDER_ID}}', '!OIDC_PROVIDER_ID!'; ^
    $content = $content -replace '{{OIDC_CLIENT_NAME}}', '!OIDC_CLIENT_NAME!'; ^
    $content = $content -replace '{{OIDC_AUTO_PROVISION}}', '!OIDC_AUTO_PROVISION!'; ^
    $content = $content -replace '{{SESSION_STORE}}', '!SESSION_STORE!'; ^
    $content = $content -replace '{{UPLOADS_DIR}}', '!UPLOADS_DIR!'; ^
    $content = $content -replace '{{THEMES_DIR}}', '!THEMES_DIR!'; ^
    $content = $content -replace '{{SEARCH_INDEX_DIR}}', '!SEARCH_INDEX_DIR!'; ^
    $content = $content -replace '{{APP_TIMEZONE}}', '!APP_TIMEZONE!'; ^
    $content = $content -replace '{{TOMCAT_PARSE_FAILED_REASON}}', '!TOMCAT_PARSE_FAILED_REASON!'; ^
    $content = $content -replace '{{TOMCAT_LOADER_CLASS}}', '!TOMCAT_LOADER_CLASS!'; ^
    $content = $content -replace '{{BANNEDWORDSLIST_FILE}}', '!BANNEDWORDSLIST_FILE!'; ^
    $content = $content -replace '{{IPBANLIST_FILE}}', '!IPBANLIST_FILE!'; ^
    $content = $content -replace '{{DB_SCRIPTS_DIR}}', '!DB_SCRIPTS_DIR!'; ^
    $content = $content -replace '{{ROLLER_VERSION_PROPS}}', '!ROLLER_VERSION_PROPS!'; ^
    $content = $content -replace '{{MAIL_HOST}}', '!MAIL_HOST!'; ^
    $content = $content -replace '{{STATIC_ASSET_CDN}}', '!STATIC_ASSET_CDN!'; ^
    Set-Content kubernetes/deployment.yaml -Value $content -Encoding UTF8"
if !ERRORLEVEL! neq 0 (echo Failed to replace placeholders & exit /b 1)

kubectl apply -f kubernetes/deployment.yaml
if !ERRORLEVEL! neq 0 (echo Failed to apply deployment & exit /b 1)

kubectl apply -f kubernetes/service.yaml
if !ERRORLEVEL! neq 0 (echo Failed to apply service & exit /b 1)

kubectl apply -f kubernetes/ingress.yaml
if !ERRORLEVEL! neq 0 (echo Failed to apply ingress & exit /b 1)

REM Wait for deployment rollout
echo.
echo === Waiting for deployment rollout ===
kubectl rollout status deployment/!PROJECT_NAME! -n !PROJECT_NAME!
if !ERRORLEVEL! neq 0 (echo Deployment rollout failed & exit /b 1)

REM Show resources
echo.
echo === Deployed resources ===
kubectl get pods,svc,ingress -n !PROJECT_NAME!

REM Show application URL
echo.
echo === Application URL ===
echo http://roller.example.com

echo.
echo === Deployment complete ===

endlocal
