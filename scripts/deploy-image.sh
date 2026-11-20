#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  The ASF licenses this file to You
# under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.  For additional information regarding
# copyright in this work, please see the NOTICE file in the top level
# directory of this distribution.

set -e
set -o pipefail

PROJECT_NAME="roller"

echo "=== Roller EKS Deployment ==="

# Ask for AWS region
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION

# Ask for EKS cluster name
read -p "Enter EKS cluster name: " CLUSTER_NAME

# Ask for image URI
read -p "Enter full image URI (registry/repo:tag): " IMAGE_URI

# Configure kubectl for the target EKS cluster
echo ""
echo "=== Configuring kubectl for EKS cluster ==="
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

# Verify cluster connectivity
echo ""
echo "=== Verifying cluster connectivity ==="
kubectl cluster-info || exit 1

# Collect environment variable values for non-credential variables
echo ""
echo "=== Enter environment variable values ==="
echo "Press Enter to skip (use placeholder)"

read -p "Enter value for DATABASE_JDBC_DRIVERCLASS (or press Enter to skip): " DATABASE_JDBC_DRIVERCLASS
read -p "Enter value for DATABASE_JDBC_CONNECTIONURL (or press Enter to skip): " DATABASE_JDBC_CONNECTIONURL
read -p "Enter value for DATABASE_JDBC_USERNAME (or press Enter to skip): " DATABASE_JDBC_USERNAME
read -p "Enter value for DATABASE_HOST (or press Enter to skip): " DATABASE_HOST
read -p "Enter value for AUTHENTICATION_METHOD (or press Enter to skip): " AUTHENTICATION_METHOD
read -p "Enter value for OIDC_ISSUER_URI (or press Enter to skip): " OIDC_ISSUER_URI
read -p "Enter value for OIDC_CLIENT_ID (or press Enter to skip): " OIDC_CLIENT_ID
read -p "Enter value for OIDC_PROVIDER_ID (or press Enter to skip): " OIDC_PROVIDER_ID
read -p "Enter value for OIDC_CLIENT_NAME (or press Enter to skip): " OIDC_CLIENT_NAME
read -p "Enter value for OIDC_AUTO_PROVISION (or press Enter to skip): " OIDC_AUTO_PROVISION
read -p "Enter value for SESSION_STORE (or press Enter to skip): " SESSION_STORE
read -p "Enter value for UPLOADS_DIR (or press Enter to skip): " UPLOADS_DIR
read -p "Enter value for THEMES_DIR (or press Enter to skip): " THEMES_DIR
read -p "Enter value for SEARCH_INDEX_DIR (or press Enter to skip): " SEARCH_INDEX_DIR
read -p "Enter value for APP_TIMEZONE (or press Enter to skip): " APP_TIMEZONE
read -p "Enter value for TOMCAT_PARSE_FAILED_REASON (or press Enter to skip): " TOMCAT_PARSE_FAILED_REASON
read -p "Enter value for TOMCAT_LOADER_CLASS (or press Enter to skip): " TOMCAT_LOADER_CLASS
read -p "Enter value for BANNEDWORDSLIST_FILE (or press Enter to skip): " BANNEDWORDSLIST_FILE
read -p "Enter value for IPBANLIST_FILE (or press Enter to skip): " IPBANLIST_FILE
read -p "Enter value for DB_SCRIPTS_DIR (or press Enter to skip): " DB_SCRIPTS_DIR
read -p "Enter value for ROLLER_VERSION_PROPS (or press Enter to skip): " ROLLER_VERSION_PROPS
read -p "Enter value for MAIL_HOST (or press Enter to skip): " MAIL_HOST
read -p "Enter value for STATIC_ASSET_CDN (or press Enter to skip): " STATIC_ASSET_CDN

# Collect credential values (without echoing)
echo ""
echo "=== Enter credential values (not echoed) ==="
read -s -p "Enter DATABASE_JDBC_PASSWORD (or press Enter to skip): " DATABASE_JDBC_PASSWORD
echo ""
read -s -p "Enter OIDC_CLIENT_SECRET (or press Enter to skip): " OIDC_CLIENT_SECRET
echo ""
read -s -p "Enter MAIL_USERNAME (or press Enter to skip): " MAIL_USERNAME
echo ""
read -s -p "Enter MAIL_PASSWORD (or press Enter to skip): " MAIL_PASSWORD
echo ""

# Apply namespace
echo ""
echo "=== Applying namespace ==="
kubectl apply -f kubernetes/namespace.yaml

# Create secrets for credentials
echo ""
echo "=== Creating Kubernetes Secret ==="
SECRET_ARGS=""
if [ -n "$DATABASE_JDBC_PASSWORD" ]; then
    SECRET_ARGS="$SECRET_ARGS --from-literal=DATABASE_JDBC_PASSWORD=$DATABASE_JDBC_PASSWORD"
fi
if [ -n "$OIDC_CLIENT_SECRET" ]; then
    SECRET_ARGS="$SECRET_ARGS --from-literal=OIDC_CLIENT_SECRET=$OIDC_CLIENT_SECRET"
fi
if [ -n "$MAIL_USERNAME" ]; then
    SECRET_ARGS="$SECRET_ARGS --from-literal=MAIL_USERNAME=$MAIL_USERNAME"
fi
if [ -n "$MAIL_PASSWORD" ]; then
    SECRET_ARGS="$SECRET_ARGS --from-literal=MAIL_PASSWORD=$MAIL_PASSWORD"
fi

if [ -n "$SECRET_ARGS" ]; then
    kubectl create secret generic ${PROJECT_NAME}-secrets -n ${PROJECT_NAME} $SECRET_ARGS --dry-run=client -o yaml | kubectl apply -f -
fi

# Replace placeholders in deployment.yaml
echo ""
echo "=== Deploying application ==="
sed -i.bak "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i.bak "s|{{DATABASE_JDBC_DRIVERCLASS}}|$DATABASE_JDBC_DRIVERCLASS|g" kubernetes/deployment.yaml
sed -i.bak "s|{{DATABASE_JDBC_CONNECTIONURL}}|$DATABASE_JDBC_CONNECTIONURL|g" kubernetes/deployment.yaml
sed -i.bak "s|{{DATABASE_JDBC_USERNAME}}|$DATABASE_JDBC_USERNAME|g" kubernetes/deployment.yaml
sed -i.bak "s|{{DATABASE_HOST}}|$DATABASE_HOST|g" kubernetes/deployment.yaml
sed -i.bak "s|{{AUTHENTICATION_METHOD}}|$AUTHENTICATION_METHOD|g" kubernetes/deployment.yaml
sed -i.bak "s|{{OIDC_ISSUER_URI}}|$OIDC_ISSUER_URI|g" kubernetes/deployment.yaml
sed -i.bak "s|{{OIDC_CLIENT_ID}}|$OIDC_CLIENT_ID|g" kubernetes/deployment.yaml
sed -i.bak "s|{{OIDC_PROVIDER_ID}}|$OIDC_PROVIDER_ID|g" kubernetes/deployment.yaml
sed -i.bak "s|{{OIDC_CLIENT_NAME}}|$OIDC_CLIENT_NAME|g" kubernetes/deployment.yaml
sed -i.bak "s|{{OIDC_AUTO_PROVISION}}|$OIDC_AUTO_PROVISION|g" kubernetes/deployment.yaml
sed -i.bak "s|{{SESSION_STORE}}|$SESSION_STORE|g" kubernetes/deployment.yaml
sed -i.bak "s|{{UPLOADS_DIR}}|$UPLOADS_DIR|g" kubernetes/deployment.yaml
sed -i.bak "s|{{THEMES_DIR}}|$THEMES_DIR|g" kubernetes/deployment.yaml
sed -i.bak "s|{{SEARCH_INDEX_DIR}}|$SEARCH_INDEX_DIR|g" kubernetes/deployment.yaml
sed -i.bak "s|{{APP_TIMEZONE}}|$APP_TIMEZONE|g" kubernetes/deployment.yaml
sed -i.bak "s|{{TOMCAT_PARSE_FAILED_REASON}}|$TOMCAT_PARSE_FAILED_REASON|g" kubernetes/deployment.yaml
sed -i.bak "s|{{TOMCAT_LOADER_CLASS}}|$TOMCAT_LOADER_CLASS|g" kubernetes/deployment.yaml
sed -i.bak "s|{{BANNEDWORDSLIST_FILE}}|$BANNEDWORDSLIST_FILE|g" kubernetes/deployment.yaml
sed -i.bak "s|{{IPBANLIST_FILE}}|$IPBANLIST_FILE|g" kubernetes/deployment.yaml
sed -i.bak "s|{{DB_SCRIPTS_DIR}}|$DB_SCRIPTS_DIR|g" kubernetes/deployment.yaml
sed -i.bak "s|{{ROLLER_VERSION_PROPS}}|$ROLLER_VERSION_PROPS|g" kubernetes/deployment.yaml
sed -i.bak "s|{{MAIL_HOST}}|$MAIL_HOST|g" kubernetes/deployment.yaml
sed -i.bak "s|{{STATIC_ASSET_CDN}}|$STATIC_ASSET_CDN|g" kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak

# Apply deployment, service, and ingress
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for deployment rollout
echo ""
echo "=== Waiting for deployment rollout ==="
kubectl rollout status deployment/${PROJECT_NAME} -n ${PROJECT_NAME}

# Show resources
echo ""
echo "=== Deployed resources ==="
kubectl get pods,svc,ingress -n ${PROJECT_NAME}

# Show application URL
echo ""
echo "=== Application URL ==="
echo "http://roller.example.com"

echo ""
echo "=== Deployment complete ==="
