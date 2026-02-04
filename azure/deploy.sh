#!/bin/bash

# SimplyCode Azure Deployment Script
# This script deploys SimplyCode to Azure Container Apps

set -e

# Configuration - Update these values
RESOURCE_GROUP="simplycode-rg"
LOCATION="eastus"
ACR_NAME="simplycodeacr"
CONTAINER_APP_ENV="simplycode-env"
BACKEND_APP="simplycode-backend"
FRONTEND_APP="simplycode-frontend"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  SimplyCode Azure Deployment          ${NC}"
echo -e "${GREEN}========================================${NC}"

# Check if logged into Azure
echo -e "\n${YELLOW}Checking Azure login status...${NC}"
if ! az account show > /dev/null 2>&1; then
    echo -e "${RED}Not logged into Azure. Please run 'az login' first.${NC}"
    exit 1
fi

SUBSCRIPTION=$(az account show --query name -o tsv)
echo -e "${GREEN}Logged in to subscription: $SUBSCRIPTION${NC}"

# Create Resource Group
echo -e "\n${YELLOW}Creating Resource Group...${NC}"
az group create --name $RESOURCE_GROUP --location $LOCATION --output none
echo -e "${GREEN}Resource Group '$RESOURCE_GROUP' created/verified${NC}"

# Create Azure Container Registry
echo -e "\n${YELLOW}Creating Azure Container Registry...${NC}"
az acr create --resource-group $RESOURCE_GROUP --name $ACR_NAME --sku Basic --admin-enabled true --output none
echo -e "${GREEN}Container Registry '$ACR_NAME' created/verified${NC}"

# Get ACR login server and credentials
ACR_LOGIN_SERVER=$(az acr show --name $ACR_NAME --query loginServer -o tsv)
ACR_USERNAME=$(az acr credential show --name $ACR_NAME --query username -o tsv)
ACR_PASSWORD=$(az acr credential show --name $ACR_NAME --query passwords[0].value -o tsv)

echo -e "${GREEN}ACR Login Server: $ACR_LOGIN_SERVER${NC}"

# Login to ACR
echo -e "\n${YELLOW}Logging into Container Registry...${NC}"
echo $ACR_PASSWORD | docker login $ACR_LOGIN_SERVER -u $ACR_USERNAME --password-stdin

# Build and push backend image
echo -e "\n${YELLOW}Building and pushing backend image...${NC}"
cd "$(dirname "$0")/.."
docker build -t $ACR_LOGIN_SERVER/simplycode-backend:latest -f docker/Dockerfile.backend .
docker push $ACR_LOGIN_SERVER/simplycode-backend:latest
echo -e "${GREEN}Backend image pushed${NC}"

# Build and push frontend image
echo -e "\n${YELLOW}Building and pushing frontend image...${NC}"
docker build -t $ACR_LOGIN_SERVER/simplycode-frontend:latest -f docker/Dockerfile.frontend .
docker push $ACR_LOGIN_SERVER/simplycode-frontend:latest
echo -e "${GREEN}Frontend image pushed${NC}"

# Create Container Apps Environment
echo -e "\n${YELLOW}Creating Container Apps Environment...${NC}"
az containerapp env create \
    --name $CONTAINER_APP_ENV \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --output none 2>/dev/null || true
echo -e "${GREEN}Container Apps Environment created/verified${NC}"

# Deploy Backend Container App
echo -e "\n${YELLOW}Deploying Backend Container App...${NC}"
az containerapp create \
    --name $BACKEND_APP \
    --resource-group $RESOURCE_GROUP \
    --environment $CONTAINER_APP_ENV \
    --image $ACR_LOGIN_SERVER/simplycode-backend:latest \
    --registry-server $ACR_LOGIN_SERVER \
    --registry-username $ACR_USERNAME \
    --registry-password $ACR_PASSWORD \
    --target-port 8080 \
    --ingress external \
    --min-replicas 1 \
    --max-replicas 5 \
    --cpu 2.0 \
    --memory 4.0Gi \
    --env-vars \
        "SPRING_PROFILES_ACTIVE=azure" \
        "JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC" \
        "EXECUTION_TIMEOUT=30000" \
        "EXECUTION_MEMORY_LIMIT=134217728" \
        "EXECUTION_MAX_OUTPUT_SIZE=65536" \
        "EXECUTION_TEMP_DIR=/tmp/simplycode" \
        "LOG_LEVEL=INFO" \
    --output none 2>/dev/null || \
az containerapp update \
    --name $BACKEND_APP \
    --resource-group $RESOURCE_GROUP \
    --image $ACR_LOGIN_SERVER/simplycode-backend:latest \
    --output none

# Get Backend URL
BACKEND_URL=$(az containerapp show --name $BACKEND_APP --resource-group $RESOURCE_GROUP --query properties.configuration.ingress.fqdn -o tsv)
echo -e "${GREEN}Backend deployed at: https://$BACKEND_URL${NC}"

# Update CORS for backend
echo -e "\n${YELLOW}Updating CORS configuration...${NC}"
az containerapp update \
    --name $BACKEND_APP \
    --resource-group $RESOURCE_GROUP \
    --set-env-vars "CORS_ALLOWED_ORIGINS=https://*.azurecontainerapps.io,http://localhost:3000" \
    --output none

# Deploy Frontend Container App
echo -e "\n${YELLOW}Deploying Frontend Container App...${NC}"
az containerapp create \
    --name $FRONTEND_APP \
    --resource-group $RESOURCE_GROUP \
    --environment $CONTAINER_APP_ENV \
    --image $ACR_LOGIN_SERVER/simplycode-frontend:latest \
    --registry-server $ACR_LOGIN_SERVER \
    --registry-username $ACR_USERNAME \
    --registry-password $ACR_PASSWORD \
    --target-port 80 \
    --ingress external \
    --min-replicas 1 \
    --max-replicas 3 \
    --cpu 0.5 \
    --memory 1.0Gi \
    --output none 2>/dev/null || \
az containerapp update \
    --name $FRONTEND_APP \
    --resource-group $RESOURCE_GROUP \
    --image $ACR_LOGIN_SERVER/simplycode-frontend:latest \
    --output none

# Get Frontend URL
FRONTEND_URL=$(az containerapp show --name $FRONTEND_APP --resource-group $RESOURCE_GROUP --query properties.configuration.ingress.fqdn -o tsv)

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  Deployment Complete!                 ${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\n${GREEN}Frontend URL: https://$FRONTEND_URL${NC}"
echo -e "${GREEN}Backend URL:  https://$BACKEND_URL${NC}"
echo -e "\n${YELLOW}Note: It may take a few minutes for the apps to start.${NC}"
echo -e "${YELLOW}Check the health endpoint: https://$BACKEND_URL/api/health${NC}"
