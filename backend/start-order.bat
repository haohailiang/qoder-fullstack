@echo off
cd /d c:\D_workspace\demo-fullstack\backend
call mvnw.cmd spring-boot:run "-Dspring-boot.run.fork=true" -pl ecommerce-order
