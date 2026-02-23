$SERVER_IP = "47.109.193.180"
$USER = "root"
$REMOTE_PUBLIC_DIR = "/opt/familysys-public"
$REMOTE_ADMIN_DIR = "/opt/familysys-admin"
$REMOTE_USER_DIR = "/opt/familysys-user"
$REMOTE_SHARED_DIR = "/opt/familysys-data"
$REMOTE_FRONTEND_DIR = "/var/www/familysys-frontend/family-frontend-remote/family"
$LOCAL_PUBLIC_JAR = "family-backend\target\family-backend-1.0-SNAPSHOT.jar"
$LOCAL_ADMIN_JAR = "family-backend-admin\target\family-backend-1.0-SNAPSHOT.jar"
$LOCAL_USER_JAR = "family-backend-user\target\family-backend-1.0-SNAPSHOT.jar"
$LOCAL_PUBLIC_DB = (Get-ChildItem -LiteralPath $PSScriptRoot -Filter "*.db" | Where-Object { $_.Name -like "family *.db" } | Select-Object -First 1)
if (-not $LOCAL_PUBLIC_DB) { Write-Error "Failed to locate public blank database"; exit 1 }
$LOCAL_PUBLIC_DB = $LOCAL_PUBLIC_DB.FullName
$LOCAL_SHARED_DB = (Get-Item -LiteralPath (Join-Path $PSScriptRoot "family.db")).FullName
$LOCAL_FRONTEND_REMOTE = "family-frontend-remote\*"
$SSH_CONFIG = "NUL"
$SSH_OPTS = @(
    "-F", $SSH_CONFIG,
    "-o", "BatchMode=no",
    "-o", "PreferredAuthentications=password",
    "-o", "PubkeyAuthentication=no",
    "-o", "PasswordAuthentication=yes",
    "-o", "ConnectTimeout=30",
    "-o", "ConnectionAttempts=3",
    "-o", "ServerAliveInterval=5",
    "-o", "ServerAliveCountMax=3",
    "-o", "ProxyCommand=none",
    "-o", "ProxyJump=none",
    "-o", "StrictHostKeyChecking=no",
    "-o", "UserKnownHostsFile=NUL"
)

Write-Host "Starting deployment to $SERVER_IP..."

# 0. Stop Service and Cleanup
Write-Host "Stopping service and performing FULL cleanup..."
ssh @SSH_OPTS "${USER}@${SERVER_IP}" "systemctl stop familysys-public familysys-admin familysys-user; rm -rf /var/www/familysys-frontend/family-frontend-remote/*; mkdir -p /opt/familysys-public /opt/familysys-admin /opt/familysys-user /opt/familysys-data /var/www/familysys-frontend/family-frontend-remote/family; rm -f /opt/familysys-public/family.db"
if ($LASTEXITCODE -ne 0) { Write-Warning "Cleanup might have had issues, proceeding..." }

# 1. Upload Backend JAR
Write-Host "Uploading backend JAR..."
scp @SSH_OPTS $LOCAL_PUBLIC_JAR "${USER}@${SERVER_IP}:${REMOTE_PUBLIC_DIR}/"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to upload public JAR"; exit 1 }
scp @SSH_OPTS $LOCAL_ADMIN_JAR "${USER}@${SERVER_IP}:${REMOTE_ADMIN_DIR}/"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to upload admin JAR"; exit 1 }
scp @SSH_OPTS $LOCAL_USER_JAR "${USER}@${SERVER_IP}:${REMOTE_USER_DIR}/"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to upload user JAR"; exit 1 }

# 2. Upload Public Database
Write-Host "Uploading public database..."
scp @SSH_OPTS $LOCAL_PUBLIC_DB "${USER}@${SERVER_IP}:${REMOTE_PUBLIC_DIR}/family.db"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to upload public database"; exit 1 }

# 2.1 Upload Shared Database
Write-Host "Uploading shared database..."
scp @SSH_OPTS $LOCAL_SHARED_DB "${USER}@${SERVER_IP}:${REMOTE_SHARED_DIR}/family.db"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to upload shared database"; exit 1 }

# 3. Upload Frontend Files
Write-Host "Uploading frontend files..."
scp @SSH_OPTS -r $LOCAL_FRONTEND_REMOTE "${USER}@${SERVER_IP}:${REMOTE_FRONTEND_DIR}/"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to upload frontend files"; exit 1 }

# 4. Restart Backend Service
Write-Host "Starting backend service..."
ssh @SSH_OPTS "${USER}@${SERVER_IP}" "systemctl start familysys-public familysys-admin familysys-user"
if ($LASTEXITCODE -ne 0) { Write-Error "Failed to start service"; exit 1 }

Write-Host "Deployment completed successfully!"
