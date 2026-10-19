Set-Location "c:\Users\shiva\OneDrive\Documents\Github\new-project"
Write-Host "Building all modules..."
$proc = Start-Process -FilePath ".\gradlew.bat" `
    -ArgumentList "build", "--no-daemon" `
    -NoNewWindow -Wait -PassThru `
    -RedirectStandardOutput "C:\Users\shiva\build-all-stdout.txt" `
    -RedirectStandardError  "C:\Users\shiva\build-all-stderr.txt"

$exitCode = $proc.ExitCode
Get-Content "C:\Users\shiva\build-all-stdout.txt" | Out-File "C:\Users\shiva\build-all-full.txt"
Get-Content "C:\Users\shiva\build-all-stderr.txt" | Out-File -Append "C:\Users\shiva\build-all-full.txt"
Add-Content "C:\Users\shiva\build-all-full.txt" "EXIT_CODE=$exitCode"
Write-Host "Exit code: $exitCode"
