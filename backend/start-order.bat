@echo off
cd /d %~dp0
call mvnw.cmd spring-boot:run "-Dspring-boot.run.fork=true" -pl ecommerce-order
