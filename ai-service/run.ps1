Set-Location $PSScriptRoot
$python = if ($env:PYTHON) {
  $env:PYTHON
} elseif (Test-Path ".\.venv\Scripts\python.exe") {
  ".\.venv\Scripts\python.exe"
} else {
  "python"
}
Write-Host "Starting ai-service on http://127.0.0.1:8084 (python: $python)"
& $python -m uvicorn app.main:app --host 127.0.0.1 --port 8084 --reload