Set-Location $PSScriptRoot
# Stop the process currently listening on 8082 (old product instance) before starting a fresh one
$conn = Get-NetTCPConnection -State Listen -LocalPort 8082 -ErrorAction SilentlyContinue
if ($conn) {
    $conn | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 3
}
& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.fork=true" -pl ecommerce-product
