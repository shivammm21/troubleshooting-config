Set-Location "c:\Users\shiva\OneDrive\Documents\Github\new-project"
Write-Host "Starting desktop-app build..."
$proc = Start-Process -FilePath ".\gradlew.bat" `
    -ArgumentList ":desktop-app:build", "--no-daemon" `
    -NoNewWindow -Wait -PassThru `
    -RedirectStandardOutput "C:\Users\shiva\desktop-stdout.txt" `
    -RedirectStandardError "C:\Users\shiva\desktop-stderr.txt"

$exitCode = $proc.ExitCode
"Exit code: $exitCode"

Get-Content "C:\Users\shiva\desktop-stdout.txt" | Out-File "C:\Users\shiva\desktop-full.txt"
Get-Content "C:\Users\shiva\desktop-stderr.txt" | Out-File -Append "C:\Users\shiva\desktop-full.txt"
Add-Content "C:\Users\shiva\desktop-full.txt" "EXIT_CODE=$exitCode"
Write-Host "Done. Output in C:\Users\shiva\desktop-full.txt"
