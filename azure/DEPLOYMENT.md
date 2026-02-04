# SimplyCode Azure Deployment Guide

## Prerequisites

1. **Azure CLI** installed and configured
2. **Docker** installed and running
3. **Azure Subscription** with sufficient permissions

## Quick Deploy

```bash
# Login to Azure
az login

# Run the deployment script
./azure/deploy.sh
```

## Manual Deployment Steps

### 1. Create Azure Resources

```bash
# Set variables
RESOURCE_GROUP="simplycode-rg"
LOCATION="eastus"
ACR_NAME="simplycodeacr"

# Create Resource Group
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create Azure Container Registry
az acr create --resource-group $RESOURCE_GROUP --name $ACR_NAME --sku Basic --admin-enabled true
```

### 2. Build and Push Docker Images

```bash
# Login to ACR
az acr login --name $ACR_NAME

# Get ACR login server
ACR_LOGIN_SERVER=$(az acr show --name $ACR_NAME --query loginServer -o tsv)

# Build and push backend
docker build -t $ACR_LOGIN_SERVER/simplycode-backend:latest -f docker/Dockerfile.backend .
docker push $ACR_LOGIN_SERVER/simplycode-backend:latest

# Build and push frontend
docker build -t $ACR_LOGIN_SERVER/simplycode-frontend:latest -f docker/Dockerfile.frontend .
docker push $ACR_LOGIN_SERVER/simplycode-frontend:latest
```

### 3. Deploy to Azure Container Apps

```bash
# Create Container Apps Environment
az containerapp env create \
    --name simplycode-env \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION

# Get ACR credentials
ACR_USERNAME=$(az acr credential show --name $ACR_NAME --query username -o tsv)
ACR_PASSWORD=$(az acr credential show --name $ACR_NAME --query passwords[0].value -o tsv)

# Deploy Backend
az containerapp create \
    --name simplycode-backend \
    --resource-group $RESOURCE_GROUP \
    --environment simplycode-env \
    --image $ACR_LOGIN_SERVER/simplycode-backend:latest \
    --registry-server $ACR_LOGIN_SERVER \
    --registry-username $ACR_USERNAME \
    --registry-password $ACR_PASSWORD \
    --target-port 8080 \
    --ingress external \
    --min-replicas 1 \
    --max-replicas 5 \
    --cpu 2.0 \
    --memory 4.0Gi

# Deploy Frontend
az containerapp create \
    --name simplycode-frontend \
    --resource-group $RESOURCE_GROUP \
    --environment simplycode-env \
    --image $ACR_LOGIN_SERVER/simplycode-frontend:latest \
    --registry-server $ACR_LOGIN_SERVER \
    --registry-username $ACR_USERNAME \
    --registry-password $ACR_PASSWORD \
    --target-port 80 \
    --ingress external \
    --min-replicas 1 \
    --max-replicas 3 \
    --cpu 0.5 \
    --memory 1.0Gi
```

### 4. Configure CORS

After deployment, get the frontend URL and update backend CORS:

```bash
# Get Frontend URL
FRONTEND_URL=$(az containerapp show --name simplycode-frontend --resource-group $RESOURCE_GROUP --query properties.configuration.ingress.fqdn -o tsv)

# Update Backend CORS
az containerapp update \
    --name simplycode-backend \
    --resource-group $RESOURCE_GROUP \
    --set-env-vars "CORS_ALLOWED_ORIGINS=https://$FRONTEND_URL,http://localhost:3000"
```

## Environment Variables

### Backend

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `azure` |
| `JAVA_OPTS` | JVM options | `-Xmx2g -Xms512m` |
| `EXECUTION_TIMEOUT` | Code execution timeout (ms) | `30000` |
| `EXECUTION_MEMORY_LIMIT` | Memory limit (bytes) | `134217728` |
| `EXECUTION_MAX_OUTPUT_SIZE` | Max output size (bytes) | `65536` |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins | - |
| `LOG_LEVEL` | Logging level | `INFO` |

### Frontend

| Variable | Description | Default |
|----------|-------------|---------|
| `BACKEND_URL` | Backend API URL | `/api` |

## Estimated Costs (Azure Container Apps)

- **Backend** (2 vCPU, 4GB RAM, 1-5 replicas): ~$50-150/month
- **Frontend** (0.5 vCPU, 1GB RAM, 1-3 replicas): ~$15-30/month
- **Container Registry** (Basic): ~$5/month
- **Total**: ~$70-185/month (depending on usage)

## Scaling

Azure Container Apps auto-scales based on HTTP traffic. Configure min/max replicas:

```bash
# Update backend scaling
az containerapp update \
    --name simplycode-backend \
    --resource-group $RESOURCE_GROUP \
    --min-replicas 2 \
    --max-replicas 10

# Update frontend scaling
az containerapp update \
    --name simplycode-frontend \
    --resource-group $RESOURCE_GROUP \
    --min-replicas 2 \
    --max-replicas 5
```

## Custom Domain

```bash
# Add custom domain to frontend
az containerapp hostname add \
    --name simplycode-frontend \
    --resource-group $RESOURCE_GROUP \
    --hostname simplycode.yourdomain.com

# Configure SSL (managed certificate)
az containerapp hostname bind \
    --name simplycode-frontend \
    --resource-group $RESOURCE_GROUP \
    --hostname simplycode.yourdomain.com \
    --environment simplycode-env \
    --validation-method CNAME
```

## Monitoring

Enable Application Insights:

```bash
# Create Application Insights
az monitor app-insights component create \
    --app simplycode-insights \
    --location $LOCATION \
    --resource-group $RESOURCE_GROUP

# Get instrumentation key
APPINSIGHTS_KEY=$(az monitor app-insights component show \
    --app simplycode-insights \
    --resource-group $RESOURCE_GROUP \
    --query instrumentationKey -o tsv)

# Update backend with App Insights
az containerapp update \
    --name simplycode-backend \
    --resource-group $RESOURCE_GROUP \
    --set-env-vars "APPLICATIONINSIGHTS_CONNECTION_STRING=InstrumentationKey=$APPINSIGHTS_KEY"
```

## Cleanup

```bash
# Delete all resources
az group delete --name $RESOURCE_GROUP --yes --no-wait
```

## Troubleshooting

### Check logs
```bash
az containerapp logs show \
    --name simplycode-backend \
    --resource-group $RESOURCE_GROUP \
    --follow
```

### Check health
```bash
BACKEND_URL=$(az containerapp show --name simplycode-backend --resource-group $RESOURCE_GROUP --query properties.configuration.ingress.fqdn -o tsv)
curl https://$BACKEND_URL/api/health
```

### Restart container
```bash
az containerapp revision restart \
    --name simplycode-backend \
    --resource-group $RESOURCE_GROUP \
    --revision <revision-name>
```
