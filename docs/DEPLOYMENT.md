# Roller Deployment Guide

## Overview

Apache Roller is a Java-based blog server built with Struts2, Velocity, ROME, and Spring. It is packaged as a WAR file and deployed in a servlet container (Apache Tomcat). This guide covers local development with Docker Compose and production deployment on AWS EKS.

## Prerequisites and System Requirements

### Local Development
- Docker 20.10+
- Docker Compose 2.0+
- Java 17 (for local builds)
- Maven 3.9+ (for local builds)

### AWS EKS Deployment
- AWS CLI 2.x
- kubectl 1.24+
- eksctl 0.149+
- IAM permissions for EKS, ECR, and Secrets Manager
- An EKS cluster (create with `eksctl create cluster`)

## Local Setup with Docker Compose

### Quick Start

1. Build and start the application:
   ```bash
   docker-compose up --build
   ```

2. Access Roller at `http://localhost:8080`

3. Stop the application:
   ```bash
   docker-compose down
   ```

### Configuration

The application reads configuration from environment variables. Create a `.env` file or pass them directly:

```bash
# Database configuration
export DATABASE_JDBC_DRIVERCLASS=org.postgresql.Driver
export DATABASE_JDBC_CONNECTIONURL=jdbc:postgresql://host:5432/rollerdb
export DATABASE_JDBC_USERNAME=roller
export DATABASE_JDBC_PASSWORD=secret

# Mail configuration
export MAIL_HOSTNAME=smtp.example.com
export MAIL_USERNAME=mailer
export MAIL_PASSWORD=secret
export MAIL_PORT=587

# Storage
export STORAGE_ROOT=/usr/local/roller/data

# Authentication
export AUTHENTICATION_METHOD=db
```

### External Services

The application expects the following external services, configured via environment variables:

| Service | Environment Variables |
|---------|----------------------|
| PostgreSQL database | `DATABASE_JDBC_CONNECTIONURL`, `DATABASE_JDBC_USERNAME`, `DATABASE_JDBC_PASSWORD` |
| Mail server | `MAIL_HOSTNAME`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_PORT` |
| LDAP (optional) | `LDAP_HOST`, `LDAP_PORT`, `LDAP_DC`, `LDAP_OU`, `LDAP_SECURITY_LEVEL` |

## Building and Pushing Container Images

### Using build-push.sh (Linux/macOS)

```bash
./scripts/build-push.sh
```

The script will:
1. Ask you to choose between AWS ECR or Docker Hub
2. Prompt for registry details
3. Build the Docker image
4. Push to the chosen registry

### Using build-push.bat (Windows)

```cmd
scripts\build-push.bat
```

### Manual Build

```bash
docker build -f Dockerfile -t <registry>/<repo>:<tag> .
```

## AWS EKS Deployment

### 1. EKS Cluster Setup

If you don't have an EKS cluster, create one:

```bash
eksctl create cluster \
  --name roller-cluster \
  --region us-east-1 \
  --nodegroup-name roller-nodes \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-max 4
```

### 2. Configure kubectl

```bash
aws eks update-kubeconfig --region us-east-1 --name roller-cluster
```

Verify connectivity:
```bash
kubectl cluster-info
```

### 3. Push Container Image to ECR

```bash
# Authenticate to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Create ECR repository
aws ecr create-repository --repository-name roller --region us-east-1

# Build and push
docker build -f Dockerfile -t <account-id>.dkr.ecr.us-east-1.amazonaws.com/roller:latest .
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/roller:latest
```

### 4. Deploy to EKS

Use the deployment script:

```bash
./scripts/deploy-image.sh
```

The script will:
1. Ask for AWS region and EKS cluster name
2. Configure kubectl
3. Collect environment variable values
4. Create a Kubernetes Secret for credentials
5. Apply namespace, deployment, service, and ingress manifests
6. Wait for the rollout to complete

### 5. Kubernetes Manifests

The deployment uses the following manifests:

#### namespace.yaml
Creates a dedicated namespace `roller` for the application.

#### deployment.yaml
Defines a Deployment with:
- 2 replicas for high availability
- Container resources: 250m CPU / 512Mi memory requests, 500m CPU / 1Gi memory limits
- Liveness and readiness probes hitting `/health` on port 8080
- Environment variables for all external dependencies
- Credentials sourced from a Kubernetes Secret

#### service.yaml
Creates a ClusterIP service exposing port 80 → 8080.

#### ingress.yaml
Creates an ALB Ingress with the AWS Load Balancer Controller:
- Annotations: `kubernetes.io/ingress.class: alb`, `alb.ingress.kubernetes.io/scheme: internet-facing`
- Host: `roller.example.com`
- Path: `/` → service port 80

### 6. Secret Management

Credentials are stored in a Kubernetes Secret named `roller-secrets`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: roller-secrets
  namespace: roller
type: Opaque
stringData:
  database-jdbc-password: <value>
  mail-password: <value>
```

The deploy script creates this secret automatically from the values you provide.

## Configuration Management

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_JDBC_CONNECTIONURL` | JDBC connection URL for the database | - |
| `DATABASE_JDBC_USERNAME` | Database username | - |
| `DATABASE_JDBC_PASSWORD` | Database password (from Secret) | - |
| `DATABASE_JDBC_DRIVERCLASS` | JDBC driver class | `org.postgresql.Driver` |
| `DATABASE_HOST` | Database host | - |
| `MAIL_HOSTNAME` | SMTP server hostname | - |
| `MAIL_USERNAME` | SMTP username | - |
| `MAIL_PASSWORD` | SMTP password (from Secret) | - |
| `MAIL_PORT` | SMTP port | - |
| `LDAP_HOST` | LDAP server host | - |
| `LDAP_PORT` | LDAP server port | - |
| `LDAP_DC` | LDAP domain components | - |
| `LDAP_OU` | LDAP organizational unit | - |
| `LDAP_SECURITY_LEVEL` | LDAP security level (none/simple/strong) | - |
| `AUTHENTICATION_METHOD` | Authentication method (db/oidc) | `db` |
| `STORAGE_ROOT` | Root directory for data files | `/usr/local/roller/data` |
| `IPBANLIST_FILE` | Path to IP ban list file | - |
| `UPLOADS_DIR` | Directory for uploaded files | - |
| `ROLLER_VERSION_PROPS` | Path to roller-version.properties file | - |
| `DB_SCRIPTS_DIR` | Directory for database scripts | - |
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |

### Configuration File

The application uses `roller-custom.properties` for configuration. In the container, this is generated at startup from environment variables by the entry point script.

## Security Considerations

1. **Secrets Management**: Never hardcode credentials. Use Kubernetes Secrets or AWS Secrets Manager.
2. **Network Policies**: Consider adding Kubernetes NetworkPolicies to restrict pod-to-pod traffic.
3. **TLS**: The ALB Ingress should use TLS termination. Configure certificates via AWS Certificate Manager (ACM).
4. **IAM Roles**: Use IRSA (IAM Roles for Service Accounts) for pods that need AWS API access.
5. **Image Scanning**: Enable ECR image scanning to detect vulnerabilities.
6. **Least Privilege**: The container runs as a non-root user.
7. **Container Hardening**: The Dockerfile uses a minimal runtime image with no package managers.

## Java Notes

### JVM Settings

The container uses the following JVM settings:

- `-Xmx512m -Xms256m`: Initial and maximum heap size
- `-XX:+UseContainerSupport`: Enable container-aware resource management (default in JDK 11+)
- `-XX:MaxRAMPercentage=75.0`: Use 75% of the container's memory limit for the heap

These settings are tuned for the container's resource limits (512Mi request, 1Gi limit). Adjust `JAVA_OPTS` as needed for your workload.

### Servlet Container

The application is a WAR file deployed in Apache Tomcat 11. The container uses:
- Tomcat 11 with JDK 17 Temurin
- The WAR is deployed as `ROOT.war` so it serves at the root context path

### Health Check Endpoint

The application exposes a health check endpoint at `/health` (HTTP GET). This endpoint returns `{"status":"UP"}` with HTTP 200 when the application is running. This is used by Kubernetes liveness and readiness probes.

### Java Version

The project is built with Java 17 (`maven.compiler.release` set to 17). The Docker build stage uses `maven:3.9.4-eclipse-temurin-17` and the runtime stage uses `eclipse-temurin:17-jdk`.

## Troubleshooting

### Pod Failures

1. **Check pod logs**:
   ```bash
   kubectl logs deployment/roller -n roller
   ```

2. **Check pod events**:
   ```bash
   kubectl describe pod -l app=roller -n roller
   ```

3. **Common issues**:
   - Database connection failures: verify `DATABASE_JDBC_CONNECTIONURL` and credentials in the Secret
   - Missing configuration: check that all required environment variables are set
   - Insufficient memory: increase memory limits in the deployment

### Service Issues

1. **Check service endpoints**:
   ```bash
   kubectl get endpoints roller-service -n roller
   ```

2. **Test connectivity from within a pod**:
   ```bash
   kubectl exec -it deployment/roller -n roller -- curl -s http://localhost:8080/health
   ```

### Ingress Problems

1. **Check ingress status**:
   ```bash
   kubectl get ingress roller-ingress -n roller
   ```

2. **Verify ALB controller is installed**:
   ```bash
   kubectl get pods -n kube-system | grep alb
   ```

3. **Check ingress events**:
   ```bash
   kubectl describe ingress roller-ingress -n roller
   ```

## Scaling and Management

### Horizontal Pod Autoscaler

To scale based on CPU utilization:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: roller-hpa
  namespace: roller
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: roller
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

Apply with:
```bash
kubectl apply -f - <<EOF
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: roller-hpa
  namespace: roller
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: roller
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
EOF
```

### Rolling Updates

To update the application image:

```bash
# Update the image
kubectl set image deployment/roller roller=<new-image> -n roller

# Or update the deployment manifest and apply
kubectl apply -f kubernetes/deployment.yaml

# Monitor the rollout
kubectl rollout status deployment/roller -n roller
```

### Rollback

If a deployment fails:

```bash
# Rollback to the previous revision
kubectl rollout undo deployment/roller -n roller

# Rollback to a specific revision
kubectl rollout history deployment/roller -n roller
kubectl rollout undo deployment/roller -n roller --to-revision=<revision>
```
