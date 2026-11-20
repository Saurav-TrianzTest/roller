# Roller Deployment Guide

This document describes how to build, deploy, and manage the Apache Roller web
application on Amazon Elastic Kubernetes Service (Amazon EKS).

## Prerequisites

### System Requirements

- **Java**: JDK 17 (for local builds)
- **Maven**: 3.8+ (for local builds)
- **Docker**: 24+ (for building and pushing container images)
- **AWS CLI**: 2.x (for ECR and EKS)
- **kubectl**: 1.28+ (for Kubernetes deployment)
- **eksctl**: 0.149+ (optional, for cluster creation)

### AWS Permissions

The IAM principal used for deployment must have permissions for:

- Amazon ECR: `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`,
  `ecr:CompleteLayerUpload`, `ecr:UploadLayerPart`, `ecr:PutImage`,
  `ecr:InitiateLayerUpload`, `ecr:DescribeRepositories`, `ecr:CreateRepository`
- Amazon EKS: `eks:DescribeCluster`, `eks:ListClusters`,
  `eks:DescribeFargateProfile`, `eks:CreateFargateProfile`
- Kubernetes (via kubectl): full access to the target namespace

## Local Setup with Docker Compose

For local development, you can use Docker Compose to run Roller with a local
PostgreSQL database:

```bash
# Set required environment variables
export DATABASE_JDBC_PASSWORD=tiger
export OIDC_CLIENT_SECRET=web_app

# Build and start
docker compose up --build
```

This starts Roller on `http://localhost:8080`. The Roller application context
path is `/`, so the application is available at the root URL.

### Environment Variables

Roller reads all configuration from environment variables. The following table
lists the key variables:

| Variable | Description | Default |
|---|---|---|
| `STORAGE_ROOT` | Root directory for media files and data | `/usr/local/tomcat/data` |
| `DATABASE_JDBC_DRIVERCLASS` | JDBC driver class | `org.postgresql.Driver` |
| `DATABASE_JDBC_CONNECTIONURL` | JDBC connection URL | `jdbc:postgresql://postgresql/rollerdb` |
| `DATABASE_JDBC_USERNAME` | Database username | `scott` |
| `DATABASE_JDBC_PASSWORD` | Database password | (required) |
| `DATABASE_HOST` | Database host for connection waiting | `postgresql:5432` |
| `AUTHENTICATION_METHOD` | `db`, `oidc`, or `db-oidc` | `db` |
| `UPLOADS_DIR` | Directory for uploaded media files | (webapp context path) |
| `THEMES_DIR` | Directory for theme files | (webapp context path) |
| `SEARCH_INDEX_DIR` | Lucene search index directory | (STORAGE_ROOT/roller_searchindex) |
| `APP_TIMEZONE` | Application timezone | System default |
| `SESSION_STORE` | Session storage backend | `in-memory` |
| `STATIC_ASSET_CDN` | CDN URL for static assets (CSS, JS, images) | (none) |
| `MAIL_HOST` | SMTP server hostname | (from config) |
| `MAIL_USERNAME` | SMTP username | (from config) |
| `MAIL_PASSWORD` | SMTP password | (from config) |

#### Cache Redis URLs

The following Redis URLs are used for distributed caching when available:

| Variable | Description |
|---|---|
| `CACHE_MANAGER_REDIS_URL` | Cache manager Redis URL |
| `SITEWIDE_CACHE_REDIS_URL` | Site-wide cache Redis URL |
| `SALT_CACHE_REDIS_URL` | Salt cache Redis URL |
| `WEBLOGFEED_CACHE_REDIS_URL` | Weblog feed cache Redis URL |
| `WEBLOGPAGE_CACHE_REDIS_URL` | Weblog page cache Redis URL |
| `PLANET_CACHE_REDIS_URL` | Planet cache Redis URL |
| `PLUGIN_CACHE_REDIS_URL` | Plugin cache Redis URL |
| `SESSION_CACHE_REDIS_URL` | Session cache Redis URL |
| `PING_CONFIG_REDIS_URL` | Ping config Redis URL |
| `HITCOUNT_QUEUE_REDIS_URL` | Hit count queue Redis URL |
| `PING_QUEUE_REDIS_URL` | Ping queue Redis URL |
| `IPBANLIST_REDIS_URL` | IP ban list Redis URL |
| `MEDIA_EXTENSIONS_REDIS_URL` | Media extensions Redis URL |

## Building and Pushing the Container Image

### Using build-push.sh (Linux/macOS)

```bash
./scripts/build-push.sh
```

The script will:
1. Ask you to choose between AWS ECR and Docker Hub
2. Collect the registry details
3. Ask for an image tag (defaults to `latest`)
4. Build the Docker image
5. Push it to the chosen registry

### Using build-push.bat (Windows)

```cmd
scripts\build-push.bat
```

The Windows script follows the same flow as the shell script.

### Manual Build

```bash
docker build -f Dockerfile -t roller:latest .
```

## EKS Deployment

### EKS Cluster Setup

If you need to create a new EKS cluster:

```bash
# Create EKS cluster
eksctl create cluster \
  --name roller-cluster \
  --region us-east-1 \
  --nodegroup-name roller-nodes \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 2 \
  --nodes-max 4

# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name roller-cluster
```

### Deploy to EKS

```bash
./scripts/deploy-image.sh
```

The script will:
1. Ask for the AWS region and EKS cluster name
2. Configure kubectl for the cluster
3. Ask for the full image URI (e.g., `123456789012.dkr.ecr.us-east-1.amazonaws.com/roller:latest`)
4. Ask for environment variable values (press Enter to skip)
5. Ask for credential values (passwords, secrets — entered without echoing)
6. Create a Kubernetes Secret for credentials
7. Replace placeholders in the deployment manifest
8. Apply namespace, deployment, service, and ingress
9. Wait for the rollout to complete
10. Show the deployed resources and application URL

### Windows Deployment

```cmd
scripts\deploy-image.bat
```

### Deployment Manifests

The `kubernetes/` directory contains:

- **namespace.yaml**: Kubernetes Namespace named `roller`
- **deployment.yaml**: Deployment with 2 replicas, resource limits, liveness and
  readiness probes on `/health`, and all environment variables
- **service.yaml**: ClusterIP Service exposing port 80 to the container port 8080
- **ingress.yaml**: Ingress with AWS Load Balancer Controller annotations,
  internet-facing scheme, and health check on `/health`

### AWS Load Balancer Controller

The ingress uses the AWS Load Balancer Controller (ALB). Ensure it is installed
on your EKS cluster:

```bash
# Install the AWS Load Balancer Controller
helm repo add eks https://aws.github.io/eks-helm
helm repo update
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=roller-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller \
  --set region=us-east-1 \
  --set vpcId=vpc-12345678
```

## Configuration Management

All configuration is externalized via environment variables. In Kubernetes:

- Non-sensitive configuration is passed as environment variables in the
  Deployment manifest
- Sensitive values (database passwords, OIDC client secrets, mail credentials)
  are stored in a Kubernetes Secret named `roller-secrets`
- The `deploy-image.sh` script creates this Secret interactively

### Health Check Endpoint

The application includes a `/health` endpoint that returns `{"status":"UP"}`
with HTTP 200. This endpoint:

- Is available from application startup for as long as the process runs
- Does not require database connectivity
- Is used by Kubernetes liveness and readiness probes

The health check is configured with:
- **Liveness probe**: 60s initial delay, 30s interval, 10s timeout, 3 failures
- **Readiness probe**: 30s initial delay, 10s interval, 5s timeout, 3 failures

## Security Considerations

### Container Security

- The container runs as a non-root user (`tomcat`)
- The base image is `tomcat:11.0-jdk17-temurin` with Oracle OpenJDK 17
- No package managers are used in the runtime stage
- The `.dockerignore` file excludes unnecessary files (node_modules, build
  artifacts, VCS files, IDE files)

### Credential Management

- Database passwords, OIDC client secrets, and mail credentials are stored in
  Kubernetes Secrets, never in plain text in manifests
- The `deploy-image.sh` script collects credentials interactively without
  echoing them
- The Dockerfile uses ARG/ENV variables that are resolved at runtime from the
  environment, not hardcoded

### Network Security

- The Service is `ClusterIP` (internal only)
- The Ingress uses an internet-facing ALB with SSL/TLS
- Session cookies are HTTP-only and use SameSite=Strict

## Java Notes

### JVM Settings

The container sets the following JVM options via `JAVA_OPTS`:

```
-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

These settings are tuned for the container's memory limits:
- **Requests**: 512Mi memory
- **Limits**: 1Gi memory

The `-XX:+UseContainerSupport` flag enables container-aware memory management.
The `-XX:MaxRAMPercentage=75.0` allows the JVM to use up to 75% of the container's
memory limit (768Mi out of 1Gi), leaving headroom for off-heap memory.

### Spring Profiles

This application is not a Spring Boot application. It uses traditional Jakarta EE
deployment with Struts2, Spring Security, and JPA/EclipseLink. There are no
Spring profiles to configure. The `SPRING_PROFILES_ACTIVE` environment variable
is not used.

### Health Endpoint

The application exposes a `/health` endpoint (served by
`HealthCheckServlet`) that returns `{"status":"UP"}` with HTTP 200 status.
This endpoint is registered in `web.xml` and is always available from
application startup. It is used by the Kubernetes liveness and readiness
probes.

### Application Startup

Roller uses a `ServletContextListener` (`RollerContext`) and an
`HttpSessionListener` (`RollerSession`) for initialization. The application
context is loaded from `/WEB-INF/security.xml` (Spring Security configuration).

## Troubleshooting

### Pod Failures

```bash
# Check pod status
kubectl get pods -n roller

# Check pod logs
kubectl logs <pod-name> -n roller

# Describe pod for events
kubectl describe pod <pod-name> -n roller
```

### Service Issues

```bash
# Check service endpoints
kubectl get endpoints -n roller

# Check service details
kubectl describe svc roller-service -n roller
```

### Ingress Problems

```bash
# Check ingress status
kubectl get ingress -n roller

# Describe ingress for events
kubectl describe ingress roller-ingress -n roller

# Check the ALB target group health
aws elb describe-target-health --target-group-arn <tg-arn>
```

### Database Connection Issues

Ensure the database is reachable from the EKS cluster:

```bash
# Test database connectivity from a debug pod
kubectl run debug --rm -it --image=postgres:16 -- bash
# Inside the pod:
psql -h <database-host> -U scott -d rollerdb
```

### Rollback

If the deployment fails:

```bash
# Rollback to the previous revision
kubectl rollout undo deployment/roller -n roller

# Check rollout status
kubectl rollout status deployment/roller -n roller

# If the new image is broken, redeploy with the previous image:
# 1. Edit the deployment to use the old image:
kubectl set image deployment/roller roller=<old-image-uri> -n roller
# 2. Or re-run deploy-image.sh with the old image URI
```

## Scaling and Management

### Horizontal Pod Autoscaler

To set up automatic scaling based on CPU utilization:

```bash
kubectl autoscale deployment roller \
  --cpu-percent=70 \
  --min=2 \
  --max=5 \
  -n roller
```

### Manual Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment roller --replicas=3 -n roller

# Check current replicas
kubectl get deployment roller -n roller
```

### Rolling Updates

The deployment uses a rolling update strategy. To update the application:

1. Build and push a new image with `scripts/build-push.sh`
2. Update the image in the deployment:
   ```bash
   kubectl set image deployment/roller roller=<new-image-uri> -n roller
   ```
3. Monitor the rollout:
   ```bash
   kubectl rollout status deployment/roller -n roller
   ```
4. Roll back if needed:
   ```bash
   kubectl rollout undo deployment/roller -n roller
   ```

### Resource Management

The deployment specifies the following resource requests and limits:

| Resource | Request | Limit |
|---|---|---|
| CPU | 250m | 500m |
| Memory | 512Mi | 1Gi |

Adjust these based on your workload. Monitor resource usage with:

```bash
kubectl top pods -n roller
```
