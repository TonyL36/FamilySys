$SERVER_IP = "47.109.193.180"
$USER = "root"
$REMOTE_BACKEND_DIR = "/opt/familysys"
$REMOTE_FRONTEND_DIR = "/var/www/familysys-frontend/family-frontend-remote/family"
$LOCAL_JAR = "family-backend\target\family-backend-1.0-SNAPSHOT.jar"
$LOCAL_DB = "family-backend\src\main\resources\family.db"
$LOCAL_FRONTEND_REMOTE = "family-frontend-remote\*"

Write-Host "Starting deployment to $SERVER_IP..."

# 0. Stop Service and Cleanup
Write-Host "Stopping service and performing FULL cleanup..."
ssh "${USER}@${SERVER_IP}" "systemctl stop familysys; rm -rf /opt/familysys/*; rm -rf /var/www/familysys-frontend/family-frontend-remote/*; mkdir -p /opt/familysys; mkdir -p /var/www/familysys-frontend/family-frontend-remote/family"
if ($LASTEXITCODE -ne 0) { Write-Warning "Cleanup might have had issues, proceeding..." }

# 1. Upload Backend JAR
Write-Host "Uploading backend JAR..."
scp $LOCAL_JAR "${USER}@${SERVER_IP}:${REMOTE_BACKEND_DIR}/"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to upload JAR"; exit 1 }

# 2. Upload Database File (Ensure persistence)
Write-Host "Uploading database file..."
scp $LOCAL_DB "${USER}@${SERVER_IP}:${REMOTE_BACKEND_DIR}/family.db"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to upload database file"; exit 1 }

# 3. Upload Frontend Files
Write-Host "Uploading frontend files..."
scp -r $LOCAL_FRONTEND_REMOTE "${USER}@${SERVER_IP}:${REMOTE_FRONTEND_DIR}/"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to upload frontend files"; exit 1 }

# 4. Restart Backend Service
Write-Host "Starting backend service..."
ssh "${USER}@${SERVER_IP}" "systemctl start familysys"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to start service"; exit 1 }

Write-Host "Deployment completed successfully!"
