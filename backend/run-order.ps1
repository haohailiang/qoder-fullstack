Set-Location $PSScriptRoot
& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.fork=true" -pl ecommerce-order
