# 🚀 Deploying a Quarkus Microservices Application to Google Kubernetes Engine (GKE) Autopilot

## A Comprehensive Step-by-Step Guide

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Phase 1 — Add Quarkus Kubernetes Extensions](#3-phase-1--add-quarkus-kubernetes-extensions)
4. [Phase 2 — Configure Application Properties for Kubernetes](#4-phase-2--configure-application-properties-for-kubernetes)
5. [Phase 3 — Generate Kubernetes Manifests with Quarkus](#5-phase-3--generate-kubernetes-manifests-with-quarkus)
6. [Phase 4 — Set up Google Cloud Project & CLI](#6-phase-4--set-up-google-cloud-project--cli)
7. [Phase 5 — Create Google Artifact Registry & Push Images](#7-phase-5--create-google-artifact-registry--push-images)
8. [Phase 6 — Create GKE Autopilot Cluster](#8-phase-6--create-gke-autopilot-cluster)
9. [Phase 7 — Write Kubernetes Manifests](#9-phase-7--write-kubernetes-manifests)
10. [Phase 8 — Deploy to GKE](#10-phase-8--deploy-to-gke)
11. [Phase 9 — Verify & Test](#11-phase-9--verify--test)
12. [Phase 10 — Connect Telegram Bot](#12-phase-10--connect-telegram-bot)
13. [Troubleshooting](#13-troubleshooting)
14. [Cleanup](#14-cleanup)
15. [Summary](#15-summary)

---

## 1. Architecture Overview

Our application consists of **three components** deployed to a single GKE Autopilot cluster:

```
┌──────────────────────────────────────────────────────────────────┐
│                     GKE Autopilot Cluster                       │
│                                                                  │
│  ┌─────────────────┐    gRPC (:9000)    ┌─────────────────────┐ │
│  │   Frontend       │ ─────────────────▶ │   Backend            │ │
│  │   Telegram Bot   │                    │   gRPC Server        │ │
│  │   (Quarkus)      │                    │   (Quarkus)          │ │
│  │   Port: 8081     │                    │   Port: 8080/9000    │ │
│  └─────────────────┘                    └──────────┬───────────┘ │
│          │                                          │             │
│          │ Telegram API                             │ MongoDB     │
│          ▼ (outbound HTTPS)                         ▼ (:27017)   │
│  ┌─────────────────┐                    ┌─────────────────────┐ │
│  │  Telegram Bot    │                    │   MongoDB 7.0        │ │
│  │  API Servers     │                    │   (StatefulSet)      │ │
│  │  (External)      │                    │   PVC Storage        │ │
│  └─────────────────┘                    └─────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

| Component | Image | Ports | Role |
|---|---|---|---|
| **Backend** | `qaserfde/words-backend-reactive:1.0.0.amd64` | HTTP `8080`, gRPC `9000` | gRPC server + MongoDB CRUD via Panache |
| **Frontend** | `qaserfde/words-telegram-bot:2.0.0.amd64` | HTTP `8081` | Telegram bot (Apache Camel) + gRPC client |
| **MongoDB** | `mongo:7.0` | `27017` | Data persistence |

> **Why GKE Autopilot?** Autopilot is Google's fully-managed Kubernetes mode—Google manages the nodes, scaling, and security for you, so you only pay for the pods you actually run. Perfect for learning and small production workloads.

> **Why Quarkus?** Quarkus is designed with a **container-first** and **Kubernetes-native** philosophy. It provides built-in extensions (`quarkus-kubernetes`, `quarkus-container-image-docker`) that automatically generate Kubernetes manifests and build container images — making the path from code to cluster seamless.

---

## 2. Prerequisites

Before starting, ensure you have:

| Tool | Minimum Version | Installation |
|---|---|---|
| **Java JDK** | 21 | [Adoptium](https://adoptium.net/) |
| **Apache Maven** | 3.9+ | `brew install maven` |
| **Docker Desktop** | 24+ | [Docker](https://www.docker.com/products/docker-desktop/) |
| **Google Cloud SDK (`gcloud`)** | Latest | [Installation Guide](https://cloud.google.com/sdk/docs/install) |
| **`kubectl`** | Latest | `gcloud components install kubectl` |
| **A GCP account** | — | With billing enabled and free trial credits |
| **Telegram Bot Token** | — | Obtained from [@BotFather](https://t.me/BotFather) |

Verify your tools:

```bash
java -version          # Should show 21+
mvn -version           # Should show 3.9+
docker --version       # Should show 24+
gcloud --version       # Should show latest
kubectl version --client # Should show latest
```

---

## 3. Phase 1 — Add Quarkus Kubernetes Extensions

Quarkus provides dedicated extensions to generate Kubernetes manifests and build/push container images. We need to add these extensions to **both** microservices.

### 3.1 Understanding the Extensions

| Extension | Artifact ID | Purpose |
|---|---|---|
| **Kubernetes** | `quarkus-kubernetes` | Auto-generates `kubernetes.yml` with Deployment + Service manifests during build |
| **Container Image Docker** | `quarkus-container-image-docker` | Builds Docker images using the local Docker daemon and pushes to a registry |
| **Kubernetes Config** | `quarkus-kubernetes-config` | *(Optional)* Reads ConfigMaps/Secrets from the Kubernetes API at runtime |

### 3.2 Add Extensions to the Backend Microservice

Navigate to your **backend** project root and run:

```bash
# Add the Kubernetes manifest generation extension
./mvnw quarkus:add-extension -Dextensions="io.quarkus:quarkus-kubernetes"

# Add the Docker container image builder extension
./mvnw quarkus:add-extension -Dextensions="io.quarkus:quarkus-container-image-docker"
```

This will add the following to your backend `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-kubernetes</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-container-image-docker</artifactId>
</dependency>
```

### 3.3 Add Extensions to the Frontend Microservice

Navigate to your **frontend** project root and run the same commands:

```bash
./mvnw quarkus:add-extension -Dextensions="io.quarkus:quarkus-kubernetes"
./mvnw quarkus:add-extension -Dextensions="io.quarkus:quarkus-container-image-docker"
```

### 3.4 Verify Extensions Were Added

In each project, confirm the extensions are listed:

```bash
./mvnw quarkus:list-extensions | grep -E "kubernetes|container-image"
```

You should see both `quarkus-kubernetes` and `quarkus-container-image-docker` in the output.

---

## 4. Phase 2 — Configure Application Properties for Kubernetes

Now we configure both microservices via `application.properties` to tell Quarkus how to generate the Kubernetes manifests and where to push Docker images.

### 4.1 Backend — `application.properties` Additions

Add the following Kubernetes-specific properties to your **backend** `src/main/resources/application.properties`:

```properties
# ============================================
# Kubernetes Deployment Configuration
# ============================================

# Target Kubernetes (not OpenShift or Knative)
quarkus.kubernetes.deployment-target=kubernetes

# Application metadata
quarkus.application.name=words-backend
quarkus.application.version=1.0.0

# ── Container Image Configuration ──
# Points to Google Artifact Registry (we'll create this in Phase 5)
quarkus.container-image.registry=REGION-docker.pkg.dev
quarkus.container-image.group=YOUR_PROJECT_ID/words-repo
quarkus.container-image.name=words-backend-reactive
quarkus.container-image.tag=1.0.0.amd64
quarkus.container-image.push=false

# ── Kubernetes Service Configuration ──
# Expose gRPC port (9000) in addition to HTTP port (8080)
quarkus.kubernetes.ports.grpc.container-port=9000
quarkus.kubernetes.ports.grpc.protocol=TCP

# ── Resource Limits (Required for GKE Autopilot) ──
quarkus.kubernetes.resources.requests.memory=256Mi
quarkus.kubernetes.resources.requests.cpu=250m
quarkus.kubernetes.resources.limits.memory=512Mi
quarkus.kubernetes.resources.limits.cpu=500m

# ── MongoDB connection string — overridden for prod ──
%prod.quarkus.mongodb.connection-string=mongodb://mongodb:27017

# ── Environment variables from Kubernetes Secrets ──
# (We'll create this Secret in Phase 7)
quarkus.kubernetes.env.secrets=mongodb-secret

# ── Image Pull Policy ──
quarkus.kubernetes.image-pull-policy=Always

# ── Health Probe Configuration ──
quarkus.kubernetes.liveness-probe.http-action-path=/q/health/live
quarkus.kubernetes.liveness-probe.initial-delay=30s
quarkus.kubernetes.liveness-probe.period=10s
quarkus.kubernetes.readiness-probe.http-action-path=/q/health/ready
quarkus.kubernetes.readiness-probe.initial-delay=5s
quarkus.kubernetes.readiness-probe.period=10s
```

> **Important Notes:**
> - Replace `REGION` with your chosen GCP region (e.g., `us-central1`, `europe-west1`)
> - Replace `YOUR_PROJECT_ID` with your actual GCP project ID
> - We'll finalize these values after creating the Artifact Registry in Phase 5
> - The `%prod` profile prefix means `quarkus.mongodb.connection-string` is **only** used in production — in dev mode, Quarkus Dev Services auto-starts MongoDB for you

### 4.2 Frontend — `application.properties` Additions

Add the following to your **frontend** `src/main/resources/application.properties`:

```properties
# ============================================
# Kubernetes Deployment Configuration
# ============================================

# Target Kubernetes
quarkus.kubernetes.deployment-target=kubernetes

# Application metadata
quarkus.application.name=words-telegram-bot
quarkus.application.version=2.0.0

# ── Container Image Configuration ──
quarkus.container-image.registry=REGION-docker.pkg.dev
quarkus.container-image.group=YOUR_PROJECT_ID/words-repo
quarkus.container-image.name=words-telegram-bot
quarkus.container-image.tag=2.0.0.amd64
quarkus.container-image.push=false

# ── Kubernetes Service Type ──
# ClusterIP is sufficient since the bot connects OUTBOUND to Telegram API
quarkus.kubernetes.service-type=ClusterIP

# ── Resource Limits (Required for GKE Autopilot) ──
quarkus.kubernetes.resources.requests.memory=256Mi
quarkus.kubernetes.resources.requests.cpu=250m
quarkus.kubernetes.resources.limits.memory=512Mi
quarkus.kubernetes.resources.limits.cpu=500m

# ── gRPC Client — Points to Backend Service inside the Cluster ──
%prod.quarkus.grpc.clients.word-service.host=words-backend
%prod.quarkus.grpc.clients.word-service.port=9000

# ── Telegram Bot Token from Kubernetes Secret ──
quarkus.kubernetes.env.mapping.telegram-bot-token.from-secret=telegram-secret
quarkus.kubernetes.env.mapping.telegram-bot-token.with-key=bot-token

# ── Image Pull Policy ──
quarkus.kubernetes.image-pull-policy=Always

# ── Health Probe Configuration ──
quarkus.kubernetes.liveness-probe.http-action-path=/q/health/live
quarkus.kubernetes.liveness-probe.initial-delay=30s
quarkus.kubernetes.liveness-probe.period=10s
quarkus.kubernetes.readiness-probe.http-action-path=/q/health/ready
quarkus.kubernetes.readiness-probe.initial-delay=5s
quarkus.kubernetes.readiness-probe.period=10s
```

### 4.3 Add SmallRye Health Extension (Both Microservices)

For health probes to work, add the SmallRye Health extension to **both** projects:

```bash
./mvnw quarkus:add-extension -Dextensions="io.quarkus:quarkus-smallrye-health"
```

This provides `/q/health/live` and `/q/health/ready` endpoints automatically.

### 4.4 Understanding the Key Configurations

| Property | Purpose |
|---|---|
| `quarkus.kubernetes.deployment-target=kubernetes` | Generates standard Kubernetes manifests (vs OpenShift/Knative) |
| `quarkus.container-image.registry` | Docker registry URL (Artifact Registry in our case) |
| `quarkus.container-image.group` | Maps to `PROJECT_ID/REPO_NAME` in Artifact Registry |
| `quarkus.kubernetes.resources.*` | **Required** for GKE Autopilot — pods won't schedule without resource requests |
| `quarkus.kubernetes.env.secrets=<name>` | Injects ALL key-value pairs from the named Secret as env vars |
| `quarkus.kubernetes.env.mapping.<var>.from-secret` | Maps a specific secret key to a specific env var |
| `%prod.` prefix | Applied **only** when running with `prod` profile (default in container) |

---

## 5. Phase 3 — Generate Kubernetes Manifests with Quarkus

Quarkus generates Kubernetes manifests during the build process. Let's generate them and review.

### 5.1 Build and Generate Manifests

In **each** microservice directory, run:

```bash
./mvnw clean package -DskipTests
```

After a successful build, Quarkus generates the manifests at:

```
target/kubernetes/
├── kubernetes.json    # JSON format
└── kubernetes.yml     # YAML format (same content)
```

### 5.2 Review the Generated Manifests

Inspect the generated YAML:

```bash
cat target/kubernetes/kubernetes.yml
```

**Example — Backend generated `kubernetes.yml`** (simplified):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app.kubernetes.io/name: words-backend
    app.kubernetes.io/version: 1.0.0
  name: words-backend
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: words-backend
      app.kubernetes.io/version: 1.0.0
  template:
    metadata:
      labels:
        app.kubernetes.io/name: words-backend
        app.kubernetes.io/version: 1.0.0
    spec:
      containers:
        - name: words-backend
          image: REGION-docker.pkg.dev/YOUR_PROJECT_ID/words-repo/words-backend-reactive:1.0.0.amd64
          ports:
            - containerPort: 8080
              name: http
              protocol: TCP
            - containerPort: 9000
              name: grpc
              protocol: TCP
          envFrom:
            - secretRef:
                name: mongodb-secret
          resources:
            requests:
              cpu: 250m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 512Mi
          livenessProbe:
            httpGet:
              path: /q/health/live
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /q/health/ready
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  labels:
    app.kubernetes.io/name: words-backend
    app.kubernetes.io/version: 1.0.0
  name: words-backend
spec:
  ports:
    - name: http
      port: 8080
      targetPort: 8080
    - name: grpc
      port: 9000
      targetPort: 9000
  selector:
    app.kubernetes.io/name: words-backend
    app.kubernetes.io/version: 1.0.0
  type: ClusterIP
```

> **💡 Key insight:** Quarkus automatically generated the Deployment and Service for you! The generated manifests include the container image reference, ports, resource limits, health probes, and secret references — all derived from your `application.properties`.

### 5.3 How to Customize Beyond Properties

For any customizations that cannot be expressed via `application.properties`, you can place raw YAML snippets in:

```
src/main/kubernetes/kubernetes.yml
```

Quarkus will **merge** your custom YAML with the auto-generated manifests. This is useful for things like adding custom annotations, init containers, or sidecars.

---

## 6. Phase 4 — Set up Google Cloud Project & CLI

### 6.1 Authenticate with Google Cloud

```bash
# Login to your Google Account
gcloud auth login

# This will open a browser window — sign in with your Google account
```

### 6.2 Create or Select a GCP Project

```bash
# List existing projects
gcloud projects list

# Create a new project (choose a globally unique ID)
gcloud projects create words-gke-project --name="Words GKE Project"

# Set as active project
gcloud config set project words-gke-project
```

> **Replace `words-gke-project`** with your own project ID throughout this guide.

### 6.3 Enable Billing

If not already enabled (required for GKE):

```bash
# List billing accounts
gcloud billing accounts list

# Link billing to project
gcloud billing projects link words-gke-project \
    --billing-account=YOUR_BILLING_ACCOUNT_ID
```

### 6.4 Enable Required APIs

```bash
# Enable all necessary APIs
gcloud services enable \
    container.googleapis.com \
    artifactregistry.googleapis.com \
    cloudbuild.googleapis.com \
    containerfilesystem.googleapis.com

# Verify they are enabled
gcloud services list --enabled --filter="NAME:(container OR artifactregistry OR cloudbuild)"
```

| API | Purpose |
|---|---|
| `container.googleapis.com` | Google Kubernetes Engine |
| `artifactregistry.googleapis.com` | Artifact Registry for Docker images |
| `cloudbuild.googleapis.com` | Cloud Build (optional, for CI/CD) |
| `containerfilesystem.googleapis.com` | Image streaming for faster pod startup |

### 6.5 Set Default Region and Zone

```bash
# Set default compute region (choose one close to you)
gcloud config set compute/region us-central1
gcloud config set compute/zone us-central1-a

# Verify configuration
gcloud config list
```

Popular regions:
- `us-central1` — Iowa, USA (generally cheapest)
- `europe-west1` — Belgium, Europe
- `asia-east1` — Taiwan, Asia

---

## 7. Phase 5 — Create Google Artifact Registry & Push Images

Google Artifact Registry is Google's recommended container registry (it replaces the older Container Registry).

### 7.1 Create the Artifact Registry Repository

```bash
# Create a Docker repository in Artifact Registry
gcloud artifacts repositories create words-repo \
    --repository-format=docker \
    --location=us-central1 \
    --description="Docker images for Words application" \
    --async
```

Verify creation:

```bash
gcloud artifacts repositories list --location=us-central1
```

You should see:

```
REPOSITORY   FORMAT  MODE                 DESCRIPTION                           LOCATION     LABELS  ...
words-repo   DOCKER  STANDARD_REPOSITORY  Docker images for Words application   us-central1
```

### 7.2 Configure Docker Authentication for Artifact Registry

Docker needs credentials to push images to Artifact Registry:

```bash
# Configure Docker to authenticate with Artifact Registry
gcloud auth configure-docker us-central1-docker.pkg.dev
```

This adds an entry to `~/.docker/config.json` so Docker can authenticate with your Artifact Registry.

### 7.3 Tag Your Local Images for Artifact Registry

Your local images need to be tagged with the full Artifact Registry path:

```bash
# Set variables for convenience
export PROJECT_ID=$(gcloud config get-value project)
export REGION=us-central1
export REPO=words-repo

# ── Tag the Backend Image ──
docker tag qaserfde/words-backend-reactive:1.0.0.amd64 \
    ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/words-backend-reactive:1.0.0.amd64

# ── Tag the Frontend Image ──
docker tag qaserfde/words-telegram-bot:2.0.0.amd64 \
    ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/words-telegram-bot:2.0.0.amd64
```

### 7.4 Push Images to Artifact Registry

```bash
# ── Push the Backend Image ──
docker push ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/words-backend-reactive:1.0.0.amd64

# ── Push the Frontend Image ──
docker push ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/words-telegram-bot:2.0.0.amd64
```

### 7.5 Verify Images in Artifact Registry

```bash
# List images in the repository
gcloud artifacts docker images list \
    ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}
```

You should see both images listed:

```
IMAGE                                                                          DIGEST        CREATE_TIME          UPDATE_TIME
us-central1-docker.pkg.dev/words-gke-project/words-repo/words-backend-reactive sha256:...    2026-03-31T12:00:00  2026-03-31T12:00:00
us-central1-docker.pkg.dev/words-gke-project/words-repo/words-telegram-bot     sha256:...    2026-03-31T12:01:00  2026-03-31T12:01:00
```

### 7.6 Update Application Properties with Actual Registry Path

Now that you know your registry path, go back and update the `application.properties` for **both** microservices:

**Backend:**
```properties
quarkus.container-image.registry=us-central1-docker.pkg.dev
quarkus.container-image.group=words-gke-project/words-repo
quarkus.container-image.name=words-backend-reactive
quarkus.container-image.tag=1.0.0.amd64
```

**Frontend:**
```properties
quarkus.container-image.registry=us-central1-docker.pkg.dev
quarkus.container-image.group=words-gke-project/words-repo
quarkus.container-image.name=words-telegram-bot
quarkus.container-image.tag=2.0.0.amd64
```

---

## 8. Phase 6 — Create GKE Autopilot Cluster

### 8.1 Create the Autopilot Cluster

```bash
gcloud container clusters create-auto words-cluster \
    --region=us-central1 \
    --project=${PROJECT_ID}
```

> ⏱ **This takes 5–10 minutes.** GKE Autopilot provisions the control plane and configures Google-managed nodes.

**What this creates:**
- A fully-managed Kubernetes control plane
- Automatic node provisioning (you don't choose machine types)
- Automatic scaling, security patching, and node upgrades
- Workload Identity enabled by default (recommended for GCP integration)

### 8.2 Get Cluster Credentials

After the cluster is created, configure `kubectl` to connect:

```bash
gcloud container clusters get-credentials words-cluster \
    --region=us-central1 \
    --project=${PROJECT_ID}
```

### 8.3 Verify Cluster Access

```bash
# Check cluster info
kubectl cluster-info

# Check nodes (Autopilot manages these — you may see some or none initially)
kubectl get nodes

# Check your kubectl context
kubectl config current-context
```

Expected output:

```
Kubernetes control plane is running at https://X.X.X.X
...
```

---

## 9. Phase 7 — Write Kubernetes Manifests

While Quarkus generates basic Deployment and Service manifests, we need additional resources: MongoDB, Secrets, and a Namespace. Let's create a complete set of manifests.

### 9.1 Project Structure for Manifests

Create a `k8s/` directory in your project root:

```bash
mkdir -p k8s
```

The final structure will be:

```
k8s/
├── namespace.yml
├── mongodb-secret.yml
├── telegram-secret.yml
├── mongodb-pvc.yml
├── mongodb-deployment.yml
├── mongodb-service.yml
├── backend-deployment.yml
├── backend-service.yml
├── frontend-deployment.yml
└── frontend-service.yml
```

### 9.2 Namespace (`namespace.yml`)

Organizing resources in a dedicated namespace is best practice:

```yaml
# k8s/namespace.yml
apiVersion: v1
kind: Namespace
metadata:
  name: words-app
  labels:
    app: words
    environment: production
```

### 9.3 MongoDB Secret (`mongodb-secret.yml`)

Store MongoDB credentials securely in a Kubernetes Secret:

```yaml
# k8s/mongodb-secret.yml
apiVersion: v1
kind: Secret
metadata:
  name: mongodb-secret
  namespace: words-app
  labels:
    app: words
    component: database
type: Opaque
stringData:
  # ── These will be available as environment variables ──
  MONGO_INITDB_ROOT_USERNAME: wordsadmin
  MONGO_INITDB_ROOT_PASSWORD: "<REPLACE_WITH_STRONG_PASSWORD>"
  MONGO_INITDB_DATABASE: words
  # ── Connection string for the backend microservice ──
  QUARKUS_MONGODB_CONNECTION_STRING: "mongodb://wordsadmin:<REPLACE_WITH_STRONG_PASSWORD>@mongodb:27017/words?authSource=admin"
```

> **⚠️ Security Note:** In a production environment, use [Google Secret Manager](https://cloud.google.com/secret-manager) or [Sealed Secrets](https://sealed-secrets.netlify.app/) instead of plain `stringData`. For this learning guide, `stringData` is acceptable but **never commit secrets to Git**.

> **💡 Why `QUARKUS_MONGODB_CONNECTION_STRING`?** Quarkus converts the property `quarkus.mongodb.connection-string` to the environment variable `QUARKUS_MONGODB_CONNECTION_STRING` automatically (dots → underscores, uppercase). When we inject the entire `mongodb-secret` into the backend pod, this env var overrides the property.

### 9.4 Telegram Bot Secret (`telegram-secret.yml`)

```yaml
# k8s/telegram-secret.yml
apiVersion: v1
kind: Secret
metadata:
  name: telegram-secret
  namespace: words-app
  labels:
    app: words
    component: frontend
type: Opaque
stringData:
  bot-token: "<YOUR_TELEGRAM_BOT_TOKEN>"
```

> **How to get a Telegram Bot Token:**
> 1. Open Telegram and search for [@BotFather](https://t.me/BotFather)
> 2. Send `/newbot` and follow the prompts
> 3. BotFather will give you a token like `123456789:ABCdefGhIJKlmNoPQRsTUVwxYZ`
> 4. Paste that token into the Secret above

### 9.5 MongoDB PersistentVolumeClaim (`mongodb-pvc.yml`)

```yaml
# k8s/mongodb-pvc.yml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mongodb-pvc
  namespace: words-app
  labels:
    app: words
    component: database
spec:
  accessModes:
    - ReadWriteOnce
  # GKE Autopilot supports the 'standard-rwo' StorageClass by default
  storageClassName: standard-rwo
  resources:
    requests:
      storage: 10Gi
```

> **Why `standard-rwo`?** GKE Autopilot provides this StorageClass by default — it provisions a persistent SSD disk backed by Google Persistent Disk.

### 9.6 MongoDB Deployment (`mongodb-deployment.yml`)

```yaml
# k8s/mongodb-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mongodb
  namespace: words-app
  labels:
    app: words
    component: database
spec:
  replicas: 1
  # ── Recreate strategy to prevent data corruption ──
  strategy:
    type: Recreate
  selector:
    matchLabels:
      app: words
      component: database
  template:
    metadata:
      labels:
        app: words
        component: database
    spec:
      containers:
        - name: mongodb
          image: mongo:7.0
          ports:
            - containerPort: 27017
              name: mongodb
              protocol: TCP
          # ── Credentials from Secret ──
          envFrom:
            - secretRef:
                name: mongodb-secret
          # ── Resource Requests (required for Autopilot) ──
          resources:
            requests:
              cpu: 250m
              memory: 512Mi
              ephemeral-storage: 1Gi
            limits:
              cpu: 1000m
              memory: 1Gi
              ephemeral-storage: 2Gi
          # ── Persistent Storage ──
          volumeMounts:
            - name: mongodb-data
              mountPath: /data/db
          # ── Health Checks ──
          readinessProbe:
            exec:
              command:
                - mongosh
                - --eval
                - "db.adminCommand('ping')"
            initialDelaySeconds: 10
            periodSeconds: 10
            timeoutSeconds: 5
          livenessProbe:
            exec:
              command:
                - mongosh
                - --eval
                - "db.adminCommand('ping')"
            initialDelaySeconds: 30
            periodSeconds: 30
            timeoutSeconds: 5
      volumes:
        - name: mongodb-data
          persistentVolumeClaim:
            claimName: mongodb-pvc
```

> **Why `strategy: Recreate`?** MongoDB doesn't support multiple instances accessing the same data volume. The `Recreate` strategy ensures the old pod is terminated before a new one starts, preventing data corruption.

### 9.7 MongoDB Service (`mongodb-service.yml`)

```yaml
# k8s/mongodb-service.yml
apiVersion: v1
kind: Service
metadata:
  name: mongodb
  namespace: words-app
  labels:
    app: words
    component: database
spec:
  # ── ClusterIP: Only accessible within the cluster ──
  type: ClusterIP
  ports:
    - port: 27017
      targetPort: 27017
      protocol: TCP
      name: mongodb
  selector:
    app: words
    component: database
```

> **Why the Service name is `mongodb`?** Our backend's connection string references `mongodb://...@mongodb:27017/...`. Kubernetes DNS resolves the service name `mongodb` to the ClusterIP of this Service, so the backend can reach MongoDB.

### 9.8 Backend Deployment (`backend-deployment.yml`)

```yaml
# k8s/backend-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: words-backend
  namespace: words-app
  labels:
    app: words
    component: backend
spec:
  replicas: 1
  selector:
    matchLabels:
      app: words
      component: backend
  template:
    metadata:
      labels:
        app: words
        component: backend
    spec:
      containers:
        - name: words-backend
          # ── Image from Artifact Registry ──
          image: us-central1-docker.pkg.dev/YOUR_PROJECT_ID/words-repo/words-backend-reactive:1.0.0.amd64
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
              name: http
              protocol: TCP
            - containerPort: 9000
              name: grpc
              protocol: TCP
          # ── Environment variables from MongoDB Secret ──
          envFrom:
            - secretRef:
                name: mongodb-secret
          # ── Additional environment variables ──
          env:
            - name: QUARKUS_PROFILE
              value: "prod"
            - name: QUARKUS_MONGODB_CONNECTION_STRING
              valueFrom:
                secretKeyRef:
                  name: mongodb-secret
                  key: QUARKUS_MONGODB_CONNECTION_STRING
          # ── Resource Requests (required for Autopilot) ──
          resources:
            requests:
              cpu: 250m
              memory: 256Mi
              ephemeral-storage: 1Gi
            limits:
              cpu: 500m
              memory: 512Mi
              ephemeral-storage: 2Gi
          # ── Health Checks ──
          livenessProbe:
            httpGet:
              path: /q/health/live
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /q/health/ready
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          startupProbe:
            httpGet:
              path: /q/health/started
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 5
            failureThreshold: 20
```

> **⚠️ Replace `YOUR_PROJECT_ID`** with your actual GCP project ID in the image path.

### 9.9 Backend Service (`backend-service.yml`)

```yaml
# k8s/backend-service.yml
apiVersion: v1
kind: Service
metadata:
  name: words-backend
  namespace: words-app
  labels:
    app: words
    component: backend
spec:
  type: ClusterIP
  ports:
    - port: 8080
      targetPort: 8080
      protocol: TCP
      name: http
    - port: 9000
      targetPort: 9000
      protocol: TCP
      name: grpc
  selector:
    app: words
    component: backend
```

> **Why the Service name is `words-backend`?** Our frontend's gRPC client is configured with `%prod.quarkus.grpc.clients.word-service.host=words-backend`. Kubernetes DNS resolves this service name so the frontend can discover the backend's gRPC endpoint.

### 9.10 Frontend Deployment (`frontend-deployment.yml`)

```yaml
# k8s/frontend-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: words-telegram-bot
  namespace: words-app
  labels:
    app: words
    component: frontend
spec:
  # ── Only 1 replica to avoid duplicate Telegram message processing ──
  replicas: 1
  selector:
    matchLabels:
      app: words
      component: frontend
  template:
    metadata:
      labels:
        app: words
        component: frontend
    spec:
      containers:
        - name: words-telegram-bot
          # ── Image from Artifact Registry ──
          image: us-central1-docker.pkg.dev/YOUR_PROJECT_ID/words-repo/words-telegram-bot:2.0.0.amd64
          imagePullPolicy: Always
          ports:
            - containerPort: 8081
              name: http
              protocol: TCP
          # ── Environment Variables ──
          env:
            - name: QUARKUS_PROFILE
              value: "prod"
            # ── Telegram Bot Token from Secret ──
            - name: TELEGRAM_BOT_TOKEN
              valueFrom:
                secretKeyRef:
                  name: telegram-secret
                  key: bot-token
            # ── gRPC Backend Connection ──
            - name: QUARKUS_GRPC_CLIENTS__WORD_SERVICE__HOST
              value: "words-backend"
            - name: QUARKUS_GRPC_CLIENTS__WORD_SERVICE__PORT
              value: "9000"
          # ── Resource Requests (required for Autopilot) ──
          resources:
            requests:
              cpu: 250m
              memory: 256Mi
              ephemeral-storage: 1Gi
            limits:
              cpu: 500m
              memory: 512Mi
              ephemeral-storage: 2Gi
          # ── Health Checks ──
          livenessProbe:
            httpGet:
              path: /q/health/live
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /q/health/ready
              port: 8081
            initialDelaySeconds: 10
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          startupProbe:
            httpGet:
              path: /q/health/started
              port: 8081
            initialDelaySeconds: 5
            periodSeconds: 5
            failureThreshold: 20
```

> **Why only 1 replica for the frontend?** The Telegram bot uses **long polling** (via Apache Camel's `telegram:bots` component). If you run multiple replicas, each would poll Telegram independently, causing duplicate message processing. For production, consider switching to Telegram's webhook mode with proper load balancing.

### 9.11 Frontend Service (`frontend-service.yml`)

```yaml
# k8s/frontend-service.yml
apiVersion: v1
kind: Service
metadata:
  name: words-telegram-bot
  namespace: words-app
  labels:
    app: words
    component: frontend
spec:
  type: ClusterIP
  ports:
    - port: 8081
      targetPort: 8081
      protocol: TCP
      name: http
  selector:
    app: words
    component: frontend
```

---

## 10. Phase 8 — Deploy to GKE

Now we deploy everything to the cluster in the correct order.

### 10.1 Deployment Order Matters!

Components must start in dependency order:

```
1. Namespace  →  2. Secrets  →  3. MongoDB (PVC + Deployment + Service)  →  4. Backend  →  5. Frontend
```

### 10.2 Step-by-Step Deployment

```bash
# ── Step 1: Create the Namespace ──
kubectl apply -f k8s/namespace.yml

# Verify
kubectl get namespaces | grep words-app
```

```bash
# ── Step 2: Create Secrets ──
kubectl apply -f k8s/mongodb-secret.yml
kubectl apply -f k8s/telegram-secret.yml

# Verify (values are hidden, but you can see the keys)
kubectl get secrets -n words-app
kubectl describe secret mongodb-secret -n words-app
kubectl describe secret telegram-secret -n words-app
```

```bash
# ── Step 3: Deploy MongoDB ──
kubectl apply -f k8s/mongodb-pvc.yml
kubectl apply -f k8s/mongodb-deployment.yml
kubectl apply -f k8s/mongodb-service.yml

# Wait for MongoDB to be ready (this may take 2–3 minutes on Autopilot)
kubectl wait --for=condition=ready pod -l component=database -n words-app --timeout=300s

# Verify MongoDB is running
kubectl get pods -n words-app -l component=database
kubectl logs -n words-app -l component=database --tail=20
```

```bash
# ── Step 4: Deploy the Backend ──
kubectl apply -f k8s/backend-deployment.yml
kubectl apply -f k8s/backend-service.yml

# Wait for Backend to be ready
kubectl wait --for=condition=ready pod -l component=backend -n words-app --timeout=300s

# Verify
kubectl get pods -n words-app -l component=backend
kubectl logs -n words-app -l component=backend --tail=30
```

```bash
# ── Step 5: Deploy the Frontend (Telegram Bot) ──
kubectl apply -f k8s/frontend-deployment.yml
kubectl apply -f k8s/frontend-service.yml

# Wait for Frontend to be ready
kubectl wait --for=condition=ready pod -l component=frontend -n words-app --timeout=300s

# Verify
kubectl get pods -n words-app -l component=frontend
kubectl logs -n words-app -l component=frontend --tail=30
```

### 10.3 Quick Deploy — All at Once

Alternatively, once you're confident the manifests are correct, deploy everything at once:

```bash
kubectl apply -f k8s/
```

### 10.4 Verify Full Deployment

```bash
# ── See all resources in the words-app namespace ──
kubectl get all -n words-app
```

Expected output:

```
NAME                                      READY   STATUS    RESTARTS   AGE
pod/mongodb-xxxxxxxxxx-xxxxx              1/1     Running   0          5m
pod/words-backend-xxxxxxxxxx-xxxxx        1/1     Running   0          3m
pod/words-telegram-bot-xxxxxxxxxx-xxxxx   1/1     Running   0          2m

NAME                         TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)             AGE
service/mongodb              ClusterIP   10.x.x.x       <none>        27017/TCP           5m
service/words-backend        ClusterIP   10.x.x.x       <none>        8080/TCP,9000/TCP   3m
service/words-telegram-bot   ClusterIP   10.x.x.x       <none>        8081/TCP            2m

NAME                                 READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/mongodb              1/1     1            1           5m
deployment.apps/words-backend        1/1     1            1           3m
deployment.apps/words-telegram-bot   1/1     1            1           2m
```

All pods should show `1/1 Running`.

---

## 11. Phase 9 — Verify & Test

### 11.1 Check Pod Logs

```bash
# ── MongoDB Logs ──
kubectl logs -n words-app deployment/mongodb --tail=50

# ── Backend Logs (look for "Quarkus started" and gRPC server messages) ──
kubectl logs -n words-app deployment/words-backend --tail=50

# ── Frontend Logs (look for Telegram bot route starting) ──
kubectl logs -n words-app deployment/words-telegram-bot --tail=50
```

**Expected in backend logs:**
```
Quarkus x.x.x on JVM started in X.XXXs.
Installed features: [cdi, grpc-server, mongodb-client, mongodb-panache, ...]
gRPC Server started on 0.0.0.0:9000
```

**Expected in frontend logs:**
```
Quarkus x.x.x on JVM started in X.XXXs.
Installed features: [camel, grpc-client, ...]
Route: telegram-quiz-bot started
```

### 11.2 Test Internal Connectivity

Verify the backend can connect to MongoDB and the frontend can reach the backend:

```bash
# ── Port-forward backend to test locally ──
kubectl port-forward -n words-app svc/words-backend 8080:8080 &

# Test the health endpoint
curl http://localhost:8080/q/health

# Stop port-forward
kill %1
```

### 11.3 Test gRPC Connectivity (Optional)

If you have `grpcurl` installed:

```bash
# Port-forward the gRPC port
kubectl port-forward -n words-app svc/words-backend 9000:9000 &

# List available gRPC services
grpcurl -plaintext localhost:9000 list

# Call getTopicCount
grpcurl -plaintext localhost:9000 backend.WordService/getTopicCount

# Stop port-forward
kill %1
```

### 11.4 Monitor Pod Events

```bash
# Watch events in real-time
kubectl get events -n words-app --sort-by='.lastTimestamp' --watch

# Check for any pod issues
kubectl describe pod -n words-app -l component=backend
kubectl describe pod -n words-app -l component=frontend
```

---

## 12. Phase 10 — Connect Telegram Bot

### 12.1 How the Telegram Bot Works in GKE

Your Telegram bot uses **Apache Camel's `telegram:bots` component**, which operates in **long-polling mode** by default. This means:

- ✅ **No inbound connections needed** — the bot polls Telegram servers for updates
- ✅ **No LoadBalancer or Ingress required** — all traffic is outbound HTTPS
- ✅ **No public IP needed** — the pod initiates connections to `api.telegram.org`
- ✅ **Works behind NAT/firewall** — GKE Autopilot provides outbound internet by default

**Long-polling flow:**
```
┌────────────────────┐         ┌────────────────────┐
│  words-telegram-bot │ ──────▶│                    │
│  (GKE Pod)          │ polling│  Telegram API       │
│                     │◀───────│  (api.telegram.org) │
│                     │ updates│                    │
└────────────────────┘         └────────────────────┘
```

### 12.2 Verify Outbound Internet Access

GKE Autopilot pods have outbound internet access by default. Verify:

```bash
# Exec into the frontend pod and test connectivity
kubectl exec -it -n words-app deployment/words-telegram-bot -- \
    curl -s https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getMe
```

> If you can't use `curl` inside the container (no curl binary), check the frontend logs instead — if the Camel Telegram route started successfully, outbound access is working.

### 12.3 Test the Bot in Telegram

1. Open Telegram on your phone or desktop
2. Search for your bot by username (the one you created with @BotFather)
3. Send `/start`
4. The bot should respond with a list of quiz topics (InlineKeyboard buttons)
5. Select a topic and start the quiz!

### 12.4 Monitor Bot Activity

Watch the frontend logs in real-time while using the bot:

```bash
kubectl logs -n words-app deployment/words-telegram-bot -f
```

You should see messages like:

```
Received from 123456789: /start
Processing /start command
Received from 123456789: <IncomingCallbackQuery>
Processing callback query (topic selection)...
```

### 12.5 (Advanced) Switching to Webhook Mode

For production deployments with multiple replicas, you'd switch from long-polling to **webhook mode**. This requires:

1. A public **HTTPS** endpoint (via Ingress + TLS certificate)
2. Configuring the Camel Telegram component in webhook mode
3. Registering the webhook URL with Telegram's API

Here's a high-level outline (for future reference):

```bash
# 1. Create a static IP
gcloud compute addresses create telegram-webhook-ip --global

# 2. Set up an Ingress with TLS (using Google-managed certificate)
# 3. Configure the Camel component:
#    camel.component.telegram.webhook-enabled=true
#    camel.component.telegram.webhook-external-url=https://your-domain.com/telegram
# 4. Register webhook with Telegram:
curl -X POST "https://api.telegram.org/bot<TOKEN>/setWebhook" \
     -d "url=https://your-domain.com/telegram"
```

> This is beyond the scope of this introductory guide, but mentioning it for awareness.

---

## 13. Troubleshooting

### 13.1 Common Issues & Solutions

| Issue | Symptoms | Solution |
|---|---|---|
| **Pods stuck in `Pending`** | `kubectl get pods` shows `Pending` | GKE Autopilot is provisioning nodes. Wait 2–5 minutes. Check events: `kubectl describe pod <name> -n words-app` |
| **`ImagePullBackOff`** | Pod can't pull image | Verify image path, check Artifact Registry permissions. The GKE service account needs `Artifact Registry Reader` role |
| **`CrashLoopBackOff`** | Pod starts and crashes repeatedly | Check logs: `kubectl logs <pod> -n words-app --previous`. Often a misconfigured environment variable or missing secret |
| **Backend can't connect to MongoDB** | Connection timeout errors in backend logs | Verify MongoDB pod is running. Check the connection string in the secret. Verify service name `mongodb` resolves |
| **Frontend can't reach Backend gRPC** | gRPC UNAVAILABLE error in frontend logs | Verify backend pod is running. Check service name `words-backend` and port `9000` |
| **Telegram bot not responding** | No reply when sending `/start` | Check frontend logs. Verify `TELEGRAM_BOT_TOKEN` is correct. Test outbound connectivity |
| **`ephemeral-storage` exceeded** | Pod evicted | Increase `ephemeral-storage` limits in deployment spec |

### 13.2 Useful Debug Commands

```bash
# ── Get comprehensive pod info ──
kubectl describe pod <pod-name> -n words-app

# ── Stream logs in real-time ──
kubectl logs -f deployment/<name> -n words-app

# ── Check previous container logs (after crash) ──
kubectl logs <pod-name> -n words-app --previous

# ── Open a shell inside a running pod ──
kubectl exec -it <pod-name> -n words-app -- /bin/bash

# ── Check DNS resolution inside a pod ──
kubectl exec -it <pod-name> -n words-app -- nslookup mongodb
kubectl exec -it <pod-name> -n words-app -- nslookup words-backend

# ── Check resource usage ──
kubectl top pods -n words-app

# ── View all events sorted by time ──
kubectl get events -n words-app --sort-by='.lastTimestamp'

# ── Restart a deployment ──
kubectl rollout restart deployment/<name> -n words-app

# ── Scale a deployment ──
kubectl scale deployment/<name> --replicas=0 -n words-app   # Scale down
kubectl scale deployment/<name> --replicas=1 -n words-app   # Scale back up
```

### 13.3 Fix Artifact Registry Permissions

If GKE can't pull images from Artifact Registry, grant the necessary role to the GKE service account:

```bash
# Get the GKE node service account
export PROJECT_NUMBER=$(gcloud projects describe ${PROJECT_ID} --format="value(projectNumber)")
export GKE_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

# Grant Artifact Registry Reader role
gcloud artifacts repositories add-iam-policy-binding words-repo \
    --location=us-central1 \
    --member="serviceAccount:${GKE_SA}" \
    --role="roles/artifactregistry.reader"
```

---

## 14. Cleanup

When you're done experimenting, clean up to avoid charges:

### 14.1 Delete Kubernetes Resources

```bash
# Delete all resources in the namespace
kubectl delete namespace words-app
```

### 14.2 Delete the GKE Cluster

```bash
# Delete the Autopilot cluster
gcloud container clusters delete words-cluster \
    --region=us-central1 \
    --project=${PROJECT_ID} \
    --quiet
```

### 14.3 Delete Artifact Registry Images

```bash
# Delete individual images
gcloud artifacts docker images delete \
    ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/words-backend-reactive:1.0.0.amd64 \
    --quiet

gcloud artifacts docker images delete \
    ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/words-telegram-bot:2.0.0.amd64 \
    --quiet

# Or delete the entire repository
gcloud artifacts repositories delete words-repo \
    --location=us-central1 \
    --quiet
```

### 14.4 Disable APIs (Optional)

```bash
gcloud services disable container.googleapis.com --force
gcloud services disable artifactregistry.googleapis.com --force
```

---

## 15. Summary

### What We Accomplished

| Phase | What We Did |
|---|---|
| **Phase 1** | Added `quarkus-kubernetes` and `quarkus-container-image-docker` Quarkus extensions to both microservices |
| **Phase 2** | Configured `application.properties` with Kubernetes-specific settings: registry, resources, secrets, health probes |
| **Phase 3** | Generated Kubernetes manifests automatically via `mvn clean package` — Quarkus created Deployment + Service YAML |
| **Phase 4** | Set up GCP: authenticated, created project, enabled APIs (GKE, Artifact Registry) |
| **Phase 5** | Created Artifact Registry repository, tagged & pushed both Docker images |
| **Phase 6** | Created a GKE Autopilot cluster and connected `kubectl` |
| **Phase 7** | Wrote complete Kubernetes manifests: Namespace, Secrets, MongoDB (PVC + Deployment + Service), Backend, Frontend |
| **Phase 8** | Deployed all components in dependency order and verified all pods are running |
| **Phase 9** | Verified connectivity: health endpoints, gRPC, pod logs |
| **Phase 10** | Confirmed Telegram bot works — long-polling requires no inbound connections, works out of the box on GKE |

### Key Quarkus-Kubernetes Takeaways

1. **Container-first by design** — Quarkus extensions generate production-ready Kubernetes manifests from `application.properties`
2. **`quarkus-kubernetes`** generates `Deployment` + `Service` automatically at build time
3. **`quarkus-container-image-docker`** builds and pushes Docker images via Maven
4. **Secrets mapping** — use `quarkus.kubernetes.env.secrets` and `quarkus.kubernetes.env.mapping` to inject K8s Secrets
5. **Profile-based config** — `%prod.` prefix separates dev from production settings
6. **GKE Autopilot** requires explicit resource requests/limits on every container — without them, pods won't schedule
7. **Telegram long-polling** works without any Ingress or public IP — the bot initiates outbound HTTPS connections

### Quick Reference — Complete Application Properties

<details>
<summary><strong>📄 Backend — Full application.properties</strong></summary>

```properties
# ── HTTP & MongoDB ──
quarkus.http.port=8080
quarkus.mongodb.database=words
%prod.quarkus.mongodb.connection-string=mongodb://wordsadmin:PASSWORD@mongodb:27017/words?authSource=admin

# ── Liquibase MongoDB ──
quarkus.liquibase-mongodb.change-log=db/changelog/db.changelog-master.yaml
quarkus.liquibase-mongodb.migrate-at-start=true

# ── gRPC Server ──
quarkus.grpc.server.port=9000
quarkus.grpc.server.test-port=9001
quarkus.grpc.server.enable-reflection-service=true

# ── Dev Services ──
quarkus.mongodb.devservices.enabled=true

# ── Kubernetes Deployment ──
quarkus.kubernetes.deployment-target=kubernetes
quarkus.application.name=words-backend
quarkus.application.version=1.0.0

# ── Container Image ──
quarkus.container-image.registry=us-central1-docker.pkg.dev
quarkus.container-image.group=YOUR_PROJECT_ID/words-repo
quarkus.container-image.name=words-backend-reactive
quarkus.container-image.tag=1.0.0.amd64
quarkus.container-image.push=false

# ── Kubernetes Resource Limits ──
quarkus.kubernetes.resources.requests.memory=256Mi
quarkus.kubernetes.resources.requests.cpu=250m
quarkus.kubernetes.resources.limits.memory=512Mi
quarkus.kubernetes.resources.limits.cpu=500m

# ── Kubernetes Ports ──
quarkus.kubernetes.ports.grpc.container-port=9000
quarkus.kubernetes.ports.grpc.protocol=TCP

# ── Kubernetes Secrets ──
quarkus.kubernetes.env.secrets=mongodb-secret

# ── Image Pull Policy ──
quarkus.kubernetes.image-pull-policy=Always

# ── Health Probes ──
quarkus.kubernetes.liveness-probe.http-action-path=/q/health/live
quarkus.kubernetes.liveness-probe.initial-delay=30s
quarkus.kubernetes.liveness-probe.period=10s
quarkus.kubernetes.readiness-probe.http-action-path=/q/health/ready
quarkus.kubernetes.readiness-probe.initial-delay=5s
quarkus.kubernetes.readiness-probe.period=10s
```

</details>

<details>
<summary><strong>📄 Frontend — Full application.properties</strong></summary>

```properties
# ── HTTP & gRPC Client ──
quarkus.http.port=8081
%prod.quarkus.grpc.clients.word-service.host=words-backend
%prod.quarkus.grpc.clients.word-service.port=9000
quarkus.grpc.dev-mode.force-server-start=false

# ── Telegram Bot ──
camel.component.telegram.authorization-token=${TELEGRAM_BOT_TOKEN}
camel.debug.enabled=true

# ── Quiz Config ──
quiz.source.lang=He
quiz.target.lang=Ru

# ── Application Metadata ──
quarkus.application.name=words-telegram-bot
quarkus.application.version=2.0.0
quarkus.log.category."org.apache.camel".level=INFO
quarkus.log.category."com.example".level=DEBUG

# ── Cache ──
quarkus.cache.caffeine.topics-cache.expire-after-write=PT30M
quarkus.cache.caffeine.topics-cache.maximum-size=1
quarkus.cache.caffeine.topics-cache.record-stats=true
quarkus.cache.caffeine.word-pairs-cache.expire-after-write=PT30M
quarkus.cache.caffeine.word-pairs-cache.maximum-size=100
quarkus.cache.caffeine.word-pairs-cache.record-stats=true
quarkus.cache.caffeine.metrics-enabled=true
quarkus.cache.topics-cache.type=caffeine

# ── Kubernetes Deployment ──
quarkus.kubernetes.deployment-target=kubernetes
quarkus.kubernetes.service-type=ClusterIP

# ── Container Image ──
quarkus.container-image.registry=us-central1-docker.pkg.dev
quarkus.container-image.group=YOUR_PROJECT_ID/words-repo
quarkus.container-image.name=words-telegram-bot
quarkus.container-image.tag=2.0.0.amd64
quarkus.container-image.push=false

# ── Kubernetes Resource Limits ──
quarkus.kubernetes.resources.requests.memory=256Mi
quarkus.kubernetes.resources.requests.cpu=250m
quarkus.kubernetes.resources.limits.memory=512Mi
quarkus.kubernetes.resources.limits.cpu=500m

# ── Telegram Token from Secret ──
quarkus.kubernetes.env.mapping.telegram-bot-token.from-secret=telegram-secret
quarkus.kubernetes.env.mapping.telegram-bot-token.with-key=bot-token

# ── Image Pull Policy ──
quarkus.kubernetes.image-pull-policy=Always

# ── Health Probes ──
quarkus.kubernetes.liveness-probe.http-action-path=/q/health/live
quarkus.kubernetes.liveness-probe.initial-delay=30s
quarkus.kubernetes.liveness-probe.period=10s
quarkus.kubernetes.readiness-probe.http-action-path=/q/health/ready
quarkus.kubernetes.readiness-probe.initial-delay=5s
quarkus.kubernetes.readiness-probe.period=10s
```

</details>

---

> **🎉 Congratulations!** You've successfully deployed a complete Quarkus microservices application — with a reactive backend, a Telegram bot frontend, and MongoDB — to Google Kubernetes Engine Autopilot. Welcome to the cloud-native world!
