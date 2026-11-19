# Deploy Milvus standalone to the remote VM over SSH.
# Usage (PowerShell):
#   $env:VM_PASS="<ssh password>"; .\scripts\deploy-milvus.ps1
# Optional overrides: $env:VM_HOST, $env:VM_USER, $env:MILVUS_REMOTE_DIR
# Requires: node with the 'ssh2' package (already used by vm-ssh.js)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$composeFile = Join-Path $repoRoot 'docker-compose.milvus.yml'
$vmHost = if ($env:VM_HOST) { $env:VM_HOST } else { '192.168.178.100' }
$remoteDir = if ($env:MILVUS_REMOTE_DIR) { $env:MILVUS_REMOTE_DIR } else { '/opt/milvus' }
$remoteCompose = "$remoteDir/docker-compose.milvus.yml"

if (-not $env:VM_PASS) {
  Write-Host "VM_PASS is not set. Manual deployment steps:" -ForegroundColor Yellow
  Write-Host "  1. Upload docker-compose.milvus.yml to ${vmHost}:${remoteDir}/"
  Write-Host "  2. ssh root@$vmHost 'cd $remoteDir && docker compose -f docker-compose.milvus.yml up -d'"
  Write-Host "  3. Verify: Test-NetConnection $vmHost -Port 19530"
  exit 1
}

Write-Host "Uploading docker-compose.milvus.yml to $vmHost ..."
& node (Join-Path $repoRoot 'vm-ssh.js') upload $composeFile $remoteCompose
if ($LASTEXITCODE -ne 0) { throw "Upload failed (exit $LASTEXITCODE)" }

$remoteCmd = "mkdir -p $remoteDir/etcd $remoteDir/minio $remoteDir/data && cd $remoteDir && (docker compose -f docker-compose.milvus.yml up -d || docker-compose -f docker-compose.milvus.yml up -d)"
Write-Host "Starting Milvus containers on $vmHost ..."
& node (Join-Path $repoRoot 'vm-ssh.js') $remoteCmd
if ($LASTEXITCODE -ne 0) { throw "Remote docker compose failed (exit $LASTEXITCODE)" }

Write-Host "Waiting for the Milvus gRPC port 19530 ..."
$deadline = (Get-Date).AddSeconds(180)
while ((Get-Date) -lt $deadline) {
  if (Test-NetConnection -ComputerName $vmHost -Port 19530 -InformationLevel Quiet) {
    Write-Host "Milvus is reachable at ${vmHost}:19530" -ForegroundColor Green
    exit 0
  }
  Start-Sleep -Seconds 5
}

Write-Warning "Port 19530 is still closed. Check container logs: docker logs milvus-standalone"