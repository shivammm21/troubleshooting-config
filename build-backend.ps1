Set-Location "c:\Users\shiva\OneDrive\Documents\Github\new-project"
Write-Host "Starting backend build..."
$proc = Start-Process -FilePath ".\gradlew.bat" `
    -ArgumentList ":backend:build", "--no-daemon" `
    -NoNewWindow -Wait -PassThru `
    -RedirectStandardOutput "C:\Users\shiva\backend-stdout.txt" `
    -RedirectStandardError "C:\Users\shiva\backend-stderr.txt"

$exitCode = $proc.ExitCode
Write-Host "Build exit code: $exitCode"

# Merge output
Get-Content "C:\Users\shiva\backend-stdout.txt" | Out-File "C:\Users\shiva\backend-full.txt"
Get-Content "C:\Users\shiva\backend-stderr.txt" | Out-File -Append "C:\Users\shiva\backend-full.txt"
Add-Content "C:\Users\shiva\backend-full.txt" "EXIT_CODE=$exitCode"
