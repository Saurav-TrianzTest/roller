#!/bin/bash
set -e
set -o pipefail

# deploy-image.sh - Deploy the Roller container image to AWS EKS
# Run from the repository root

echo "=== Deploying Roller to AWS EKS ==="

# Ask for AWS region and EKS cluster name
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
read -p "Enter EKS cluster name: " CLUSTER_NAME
read -p "Enter full image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/roller:latest): " IMAGE_URI

echo ""
echo "=== Configuring kubectl for EKS cluster: $CLUSTER_NAME ==="
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl"
    exit 1
fi

echo ""
echo "=== Checking cluster connectivity ==="
kubectl cluster-info || { echo "ERROR: Cannot connect to cluster"; exit 1; }

echo ""
echo "=== Collecting environment variable values ==="
echo "Enter values for each environment variable (or press Enter to skip)."
echo "For credentials, enter the value without echoing."

# Ask for each environment variable
read -p "Enter value for DATABASE_JDBC_CONNECTIONURL (or press Enter to skip): " DATABASE_JDBC_CONNECTIONURL
read -p "Enter value for DATABASE_JDBC_USERNAME (or press Enter to skip): " DATABASE_JDBC_USERNAME
read -s -p "Enter value for DATABASE_JDBC_PASSWORD (or press Enter to skip): " DATABASE_JDBC_PASSWORD
echo ""
read -p "Enter value for DATABASE_HOST (or press Enter to skip): " DATABASE_HOST
read -p "Enter value for AUTHENTICATION_METHOD (or press Enter to skip): " AUTHENTICATION_METHOD
read -p "Enter value for MAIL_HOSTNAME (or press Enter to skip): " MAIL_HOSTNAME
read -p "Enter value for MAIL_USERNAME (or press Enter to skip): " MAIL_USERNAME
read -s -p "Enter value for MAIL_PASSWORD (or press Enter to skip): " MAIL_PASSWORD
echo ""
read -p "Enter value for MAIL_PORT (or press Enter to skip): " MAIL_PORT
read -p "Enter value for LDAP_HOST (or press Enter to skip): " LDAP_HOST
read -p "Enter value for LDAP_PORT (or press Enter to skip): " LDAP_PORT
read -p "Enter value for LDAP_DC (or press Enter to skip): " LDAP_DC
read -p "Enter value for LDAP_OU (or press Enter to skip): " LDAP_OU
read -p "Enter value for LDAP_SECURITY_LEVEL (or press Enter to skip): " LDAP_SECURITY_LEVEL
read -p "Enter value for IPBANLIST_FILE (or press Enter to skip): " IPBANLIST_FILE
read -p "Enter value for UPLOADS_DIR (or press Enter to skip): " UPLOADS_DIR
read -p "Enter value for ROLLER_VERSION_PROPS (or press Enter to skip): " ROLLER_VERSION_PROPS
read -p "Enter value for DB_SCRIPTS_DIR (or press Enter to skip): " DB_SCRIPTS_DIR

echo ""
echo "=== Creating Kubernetes Secret for credentials ==="
SECRET_ARGS=""
if [ -n "$DATABASE_JDBC_PASSWORD" ]; then
    SECRET_ARGS="$SECRET_ARGS --from-literal=database-jdbc-password=$DATABASE_JDBC_PASSWORD"
fi
if [ -n "$MAIL_PASSWORD" ]; then
    SECRET_ARGS="$SECRET_ARGS --from-literal=mail-password=$MAIL_PASSWORD"
fi

if [ -n "$SECRET_ARGS" ]; then
    kubectl create secret generic roller-secrets -n roller $SECRET_ARGS --dry-run=client -o yaml | kubectl apply -f -
    echo "Secret 'roller-secrets' created successfully."
else
    echo "No credentials provided. Skipping secret creation."
fi

echo ""
echo "=== Applying namespace ==="
kubectl apply -f kubernetes/namespace.yaml

echo ""
echo "=== Applying deployment with placeholder substitution ==="
# Replace placeholders in deployment.yaml
sed -i.bak 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{DATABASE_JDBC_CONNECTIONURL}}|'"$DATABASE_JDBC_CONNECTIONURL"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{DATABASE_JDBC_USERNAME}}|'"$DATABASE_JDBC_USERNAME"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{DATABASE_HOST}}|'"$DATABASE_HOST"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{AUTHENTICATION_METHOD}}|'"$AUTHENTICATION_METHOD"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{MAIL_HOSTNAME}}|'"$MAIL_HOSTNAME"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{MAIL_USERNAME}}|'"$MAIL_USERNAME"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{MAIL_PORT}}|'"$MAIL_PORT"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{LDAP_HOST}}|'"$LDAP_HOST"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{LDAP_PORT}}|'"$LDAP_PORT"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{LDAP_DC}}|'"$LDAP_DC"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{LDAP_OU}}|'"$LDAP_OU"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{LDAP_SECURITY_LEVEL}}|'"$LDAP_SECURITY_LEVEL"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{IPBANLIST_FILE}}|'"$IPBANLIST_FILE"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{UPLOADS_DIR}}|'"$UPLOADS_DIR"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{ROLLER_VERSION_PROPS}}|'"$ROLLER_VERSION_PROPS"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{DB_SCRIPTS_DIR}}|'"$DB_SCRIPTS_DIR"'|g' kubernetes/deployment.yaml
# Clean up backup files
rm -f kubernetes/deployment.yaml.bak

kubectl apply -f kubernetes/deployment.yaml

echo ""
echo "=== Applying service ==="
kubectl apply -f kubernetes/service.yaml

echo ""
echo "=== Applying ingress ==="
kubectl apply -f kubernetes/ingress.yaml

echo ""
echo "=== Waiting for deployment rollout ==="
kubectl rollout status deployment/roller -n roller

echo ""
echo "=== Deployment status ==="
kubectl get pods,svc,ingress -n roller

echo ""
echo "=== Application URL ==="
kubectl get ingress roller-ingress -n roller -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "Ingress hostname not yet available. Check with: kubectl get ingress -n roller"

echo ""
echo "=== Deployment complete ==="
echo "If the deployment fails, you can rollback with:"
echo "  kubectl rollout undo deployment/roller -n roller"
