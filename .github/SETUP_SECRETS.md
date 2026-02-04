# GitHub Secrets Setup for Azure Deployment

This document explains how to configure the required GitHub secrets for the CI/CD pipeline.

## Required Secret

### `AZURE_CREDENTIALS`

This secret contains the Azure service principal credentials needed to deploy to Azure Container Apps.

#### Step 1: Create a Service Principal

Run this command in Azure CLI (or Azure Cloud Shell):

```bash
az ad sp create-for-rbac \
  --name "simplycode-github-actions" \
  --role contributor \
  --scopes /subscriptions/{subscription-id}/resourceGroups/simplycode-rg \
  --sdk-auth
```

Replace `{subscription-id}` with your Azure subscription ID.

You can find your subscription ID by running:
```bash
az account show --query id -o tsv
```

#### Step 2: Copy the Output

The command will output JSON like this:

```json
{
  "clientId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "clientSecret": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "subscriptionId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "tenantId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "activeDirectoryEndpointUrl": "https://login.microsoftonline.com",
  "resourceManagerEndpointUrl": "https://management.azure.com/",
  "activeDirectoryGraphResourceId": "https://graph.windows.net/",
  "sqlManagementEndpointUrl": "https://management.core.windows.net:8443/",
  "galleryEndpointUrl": "https://gallery.azure.com/",
  "managementEndpointUrl": "https://management.core.windows.net/"
}
```

#### Step 3: Add the Secret to GitHub

1. Go to your GitHub repository
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Name: `AZURE_CREDENTIALS`
5. Value: Paste the entire JSON output from Step 2
6. Click **Add secret**

## Granting ACR Access (if needed)

If the service principal needs access to push images to Azure Container Registry:

```bash
# Get the ACR resource ID
ACR_ID=$(az acr show --name simplycodeacr --query id -o tsv)

# Grant AcrPush role to the service principal
az role assignment create \
  --assignee {clientId-from-step-2} \
  --role AcrPush \
  --scope $ACR_ID
```

## Verifying the Setup

After adding the secret:

1. Go to the **Actions** tab in your repository
2. Click on **Deploy to Azure** workflow
3. Click **Run workflow** to manually trigger a deployment
4. Check the logs to verify everything works

## Troubleshooting

### "AADSTS700016: Application not found"
- The service principal may have been deleted. Create a new one.

### "AuthorizationFailed"
- The service principal doesn't have permissions. Run the role assignment command again.

### "ACR login failed"
- The service principal needs AcrPush role. Run the ACR access command above.

## Security Notes

- Never commit credentials to the repository
- Rotate the service principal credentials periodically
- Use the minimum required permissions (Contributor on resource group)
- Consider using Azure Managed Identity for enhanced security in production
