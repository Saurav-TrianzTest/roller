@echo off
setlocal enabledelayedexpansion

REM deploy-image.bat - Deploy the Roller container image to AWS EKS
REM Run from the repository root

echo === Deploying Roller to AWS EKS ===

set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter EKS cluster name: "
set /p IMAGE_URI="Enter full image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/roller:latest): "

echo.
echo === Configuring kubectl for EKS cluster: !CLUSTER_NAME! ===
call aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl
    exit /b 1
)

echo.
echo === Checking cluster connectivity ===
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to cluster
    exit /b 1
)

echo.
echo === Collecting environment variable values ===
echo Enter values for each environment variable ^(or press Enter to skip^).

set /p DATABASE_JDBC_CONNECTIONURL="Enter value for DATABASE_JDBC_CONNECTIONURL (or press Enter to skip): "
set /p DATABASE_JDBC_USERNAME="Enter value for DATABASE_JDBC_USERNAME (or press Enter to skip): "
set /p DATABASE_JDBC_PASSWORD="Enter value for DATABASE_JDBC_PASSWORD (or press Enter to skip): "
set /p DATABASE_HOST="Enter value for DATABASE_HOST (or press Enter to skip): "
set /p AUTHENTICATION_METHOD="Enter value for AUTHENTICATION_METHOD (or press Enter to skip): "
set /p MAIL_HOSTNAME="Enter value for MAIL_HOSTNAME (or press Enter to skip): "
set /p MAIL_USERNAME="Enter value for MAIL_USERNAME (or press Enter to skip): "
set /p MAIL_PASSWORD="Enter value for MAIL_PASSWORD (or press Enter to skip): "
set /p MAIL_PORT="Enter value for MAIL_PORT (or press Enter to skip): "
set /p LDAP_HOST="Enter value for LDAP_HOST (or press Enter to skip): "
set /p LDAP_PORT="Enter value for LDAP_PORT (or press Enter to skip): "
set /p LDAP_DC="Enter value for LDAP_DC (or press Enter to skip): "
set /p LDAP_OU="Enter value for LDAP_OU (or press Enter to skip): "
set /p LDAP_SECURITY_LEVEL="Enter value for LDAP_SECURITY_LEVEL (or press Enter to skip): "
set /p IPBANLIST_FILE="Enter value for IPBANLIST_FILE (or press Enter to skip): "
set /p UPLOADS_DIR="Enter value for UPLOADS_DIR (or press Enter to skip): "
set /p ROLLER_VERSION_PROPS="Enter value for ROLLER_VERSION_PROPS (or press Enter to skip): "
set /p DB_SCRIPTS_DIR="Enter value for DB_SCRIPTS_DIR (or press Enter to skip): "

echo.
echo === Creating Kubernetes Secret for credentials ===
set "SECRET_ARGS="
if not "!DATABASE_JDBC_PASSWORD!"=="" (
    set "SECRET_ARGS=!SECRET_ARGS! --from-literal=database-jdbc-password=!DATABASE_JDBC_PASSWORD!"
)
if not "!MAIL_PASSWORD!"=="" (
    set "SECRET_ARGS=!SECRET_ARGS! --from-literal=mail-password=!MAIL_PASSWORD!"
)

if not "!SECRET_ARGS!"=="" (
    call kubectl create secret generic roller-secrets -n roller !SECRET_ARGS! --dry-run=client -o yaml | kubectl apply -f -
    echo Secret 'roller-secrets' created successfully.
) else (
    echo No credentials provided. Skipping secret creation.
)

echo.
echo === Applying namespace ===
call kubectl apply -f kubernetes/namespace.yaml

echo.
echo === Applying deployment with placeholder substitution ===
REM Replace placeholders in deployment.yaml using sed
call sed -i.bak "s|{{IMAGE_URI}}|!IMAGE_URI!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{DATABASE_JDBC_CONNECTIONURL}}|!DATABASE_JDBC_CONNECTIONURL!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{DATABASE_JDBC_USERNAME}}|!DATABASE_JDBC_USERNAME!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{DATABASE_HOST}}|!DATABASE_HOST!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{AUTHENTICATION_METHOD}}|!AUTHENTICATION_METHOD!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{MAIL_HOSTNAME}}|!MAIL_HOSTNAME!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{MAIL_USERNAME}}|!MAIL_USERNAME!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{MAIL_PORT}}|!MAIL_PORT!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{LDAP_HOST}}|!LDAP_HOST!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{LDAP_PORT}}|!LDAP_PORT!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{LDAP_DC}}|!LDAP_DC!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{LDAP_OU}}|!LDAP_OU!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{LDAP_SECURITY_LEVEL}}|!LDAP_SECURITY_LEVEL!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{IPBANLIST_FILE}}|!IPBANLIST_FILE!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{UPLOADS_DIR}}|!UPLOADS_DIR!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{ROLLER_VERSION_PROPS}}|!ROLLER_VERSION_PROPS!|g" kubernetes/deployment.yaml
call sed -i.bak "s|{{DB_SCRIPTS_DIR}}|!DB_SCRIPTS_DIR!|g" kubernetes/deployment.yaml
REM Clean up backup files
del kubernetes/deployment.yaml.bak

call kubectl apply -f kubernetes/deployment.yaml

echo.
echo === Applying service ===
call kubectl apply -f kubernetes/service.yaml

echo.
echo === Applying ingress ===
call kubectl apply -f kubernetes/ingress.yaml

echo.
echo === Waiting for deployment rollout ===
call kubectl rollout status deployment/roller -n roller
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed
    exit /b 1
)

echo.
echo === Deployment status ===
call kubectl get pods,svc,ingress -n roller

echo.
echo === Deployment complete ===
echo If the deployment fails, you can rollback with:
echo   kubectl rollout undo deployment/roller -n roller

endlocal
