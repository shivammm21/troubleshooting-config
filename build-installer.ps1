<#
.SYNOPSIS
    Builds the StackPilot Windows setup executable.

.DESCRIPTION
    Produces a single self-contained .exe installer (no WiX, no external
    packaging tools):

      1. Generates installer/StackPilot.ico from the desktop logo.
      2. Runs :desktop-app:createDistributable (jpackage app image).
      3. Zips the app image into a payload archive.
      4. Compiles installer/Setup.cs into a native bootstrapper with csc.exe.
      5. Concatenates bootstrapper + payload + trailer into
         dist/StackPilot-Setup-<version>.exe.
      6. Verifies the produced executable's embedded payload.

    The resulting setup installs for all users into
    %ProgramFiles%\StackPilot, creates Start-menu and desktop shortcuts and
    registers an entry in Settings > Apps.

.PARAMETER SkipBuild
    Reuse the existing app image instead of running Gradle again.

.PARAMETER SkipPayload
    Reuse installer/obj/payload.zip when it already exists (bootstrapper-only iterations).

.EXAMPLE
    .\build-installer.ps1

.EXAMPLE
    .\build-installer.ps1 -SkipBuild
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipPayload
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = $PSScriptRoot
$installerDir = Join-Path $root 'installer'
$objDir = Join-Path $installerDir 'obj'
$distDir = Join-Path $root 'dist'

$appImageParent = Join-Path $root 'desktop-build\compose\binaries\main\app'
$appImageDir = Join-Path $appImageParent 'StackPilot'

$gradleFile = Join-Path $root 'desktop-app\build.gradle.kts'
$setupSource = Join-Path $installerDir 'Setup.cs'
$manifestFile = Join-Path $installerDir 'app.manifest'
$iconFile = Join-Path $installerDir 'StackPilot.ico'
$pngIconFile = Join-Path $installerDir 'StackPilot.png'

function Write-Step {
    param([string]$Message)
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-PackageVersion {
    $match = Select-String -Path $gradleFile -Pattern 'packageVersion\s*=\s*"([^"]+)"' |
        Select-Object -First 1
    if (-not $match) {
        throw "Could not read packageVersion from $gradleFile"
    }
    return $match.Matches[0].Groups[1].Value
}

# ─── 1. Icon ────────────────────────────────────────────────────────────────
function New-AppIcon {
    $logo = Join-Path $root 'desktop-app\src\main\resources\drawable\logo-white.png'
    if (-not (Test-Path $logo)) {
        throw "Logo not found: $logo"
    }

    if ((Test-Path $iconFile) -and (Test-Path $pngIconFile) -and
        ((Get-Item $iconFile).LastWriteTime -gt (Get-Item $logo).LastWriteTime) -and
        ((Get-Item $pngIconFile).LastWriteTime -gt (Get-Item $logo).LastWriteTime)) {
        Write-Host "Icons up to date: $iconFile, $pngIconFile"
        return
    }

    Write-Host "Generating $iconFile and $pngIconFile"

    Add-Type -AssemblyName System.Drawing
    $image = [System.Drawing.Image]::FromFile($logo)
    try {
        $bitmap = New-Object System.Drawing.Bitmap 256, 256
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
            $graphics.Clear([System.Drawing.Color]::Transparent)

            $scale = [Math]::Min(256.0 / $image.Width, 256.0 / $image.Height)
            $width = [int]($image.Width * $scale)
            $height = [int]($image.Height * $scale)
            $graphics.DrawImage($image, [int]((256 - $width) / 2), [int]((256 - $height) / 2), $width, $height)
        }
        finally {
            $graphics.Dispose()
        }

        $stream = New-Object System.IO.MemoryStream
        try {
            $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
            $pngBytes = $stream.ToArray()
        }
        finally {
            $stream.Dispose()
            $bitmap.Dispose()
        }
    }
    finally {
        $image.Dispose()
    }

    # An ICO file holding a single 256x256 PNG-compressed image (Vista+).
    $iconStream = [System.IO.File]::Create($iconFile)
    $writer = New-Object System.IO.BinaryWriter($iconStream)
    try {
        $writer.Write([UInt16]0)                      # reserved
        $writer.Write([UInt16]1)                      # type: icon
        $writer.Write([UInt16]1)                      # image count
        $writer.Write([Byte]0)                        # width  (0 = 256)
        $writer.Write([Byte]0)                        # height (0 = 256)
        $writer.Write([Byte]0)                        # palette
        $writer.Write([Byte]0)                        # reserved
        $writer.Write([UInt16]1)                      # color planes
        $writer.Write([UInt16]32)                     # bits per pixel
        $writer.Write([UInt32]$pngBytes.Length)       # image size
        $writer.Write([UInt32]22)                     # image offset
        $writer.Write($pngBytes)
    }
    finally {
        $writer.Dispose()
        $iconStream.Dispose()
    }

    Write-Host "Icon written ($((Get-Item $iconFile).Length) bytes)"

    # jpackage's Linux (deb) bundler wants a PNG icon.
    [System.IO.File]::WriteAllBytes($pngIconFile, $pngBytes)
    Write-Host "Linux icon written: $pngIconFile ($((Get-Item $pngIconFile).Length) bytes)"
}

# ─── 2. App image ───────────────────────────────────────────────────────────
function Invoke-AppImageBuild {
    $jdkHome = $env:JAVA_HOME
    if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome 'bin\jpackage.exe'))) {
        $candidate = 'C:\Program Files\Java\jdk-21'
        if (-not (Test-Path (Join-Path $candidate 'bin\jpackage.exe'))) {
            throw 'JDK 21 with jpackage was not found. Set JAVA_HOME to a JDK 21 installation.'
        }
        $jdkHome = $candidate
    }
    Write-Host "Using JDK: $jdkHome"

    $previousJavaHome = $env:JAVA_HOME
    $env:JAVA_HOME = $jdkHome
    try {
        Push-Location $root
        try {
            & (Join-Path $root 'gradlew.bat') ':desktop-app:createDistributable' '--no-daemon' '--console=plain'
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle createDistributable failed with exit code $LASTEXITCODE"
            }
        }
        finally {
            Pop-Location
        }
    }
    finally {
        $env:JAVA_HOME = $previousJavaHome
    }
}

# ─── 3. Payload ─────────────────────────────────────────────────────────────
function New-PayloadArchive {
    param([string]$Destination)

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    if (Test-Path $Destination) {
        Remove-Item $Destination -Force
    }

    $source = (Resolve-Path $appImageParent).Path.TrimEnd('\')
    $files = @(Get-ChildItem -Path $source -Recurse -File)
    $directories = @(Get-ChildItem -Path $source -Recurse -Directory)
    Write-Host "Compressing $($files.Count) files (this can take a few minutes)…"

    # ZipArchive is used instead of ZipFile.CreateFromDirectory because the
    # .NET Framework implementation stores entry names with backslashes, which
    # is not what the ZIP specification (or non-Windows unzippers) expect.
    $zipStream = [System.IO.File]::Create($Destination)
    try {
        $archive = New-Object System.IO.Compression.ZipArchive(
            $zipStream,
            [System.IO.Compression.ZipArchiveMode]::Create,
            $false)
        try {
            foreach ($directory in $directories) {
                $relative = $directory.FullName.Substring($source.Length + 1).Replace('\', '/') + '/'
                $archive.CreateEntry($relative) | Out-Null
            }

            $index = 0
            foreach ($file in $files) {
                $relative = $file.FullName.Substring($source.Length + 1).Replace('\', '/')
                $entry = $archive.CreateEntry(
                    $relative,
                    [System.IO.Compression.CompressionLevel]::Optimal)
                $entry.LastWriteTime = [DateTimeOffset]$file.LastWriteTime

                $entryStream = $entry.Open()
                try {
                    $input = [System.IO.File]::OpenRead($file.FullName)
                    try {
                        $input.CopyTo($entryStream)
                    }
                    finally {
                        $input.Dispose()
                    }
                }
                finally {
                    $entryStream.Dispose()
                }

                $index++
                if (($index % 100) -eq 0) {
                    Write-Host "  $index / $($files.Count) files"
                }
            }
        }
        finally {
            $archive.Dispose()
        }
    }
    finally {
        $zipStream.Dispose()
    }

    $sizeMb = [Math]::Round((Get-Item $Destination).Length / 1MB, 1)
    Write-Host "Payload written: $Destination ($sizeMb MB)"
}

# ─── 4. Bootstrapper ────────────────────────────────────────────────────────
function New-Bootstrapper {
    param([string]$Destination)

    $csc = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    if (-not (Test-Path $csc)) {
        $csc = 'C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe'
    }
    if (-not (Test-Path $csc)) {
        throw 'csc.exe (.NET Framework compiler) was not found.'
    }

    if (Test-Path $Destination) {
        Remove-Item $Destination -Force
    }

    $arguments = @(
        '/nologo'
        '/target:winexe'
        '/platform:anycpu'
        '/optimize+'
        '/warn:4'
        "/win32icon:$iconFile"
        "/win32manifest:$manifestFile"
        "/out:$Destination"
        '/r:System.dll'
        '/r:System.Core.dll'
        '/r:System.Drawing.dll'
        '/r:System.Windows.Forms.dll'
        '/r:System.IO.Compression.dll'
        '/r:System.IO.Compression.FileSystem.dll'
        '/r:Microsoft.CSharp.dll'
        $setupSource
    )

    Write-Host "Compiling bootstrapper with $csc"
    & $csc $arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Bootstrapper compilation failed with exit code $LASTEXITCODE"
    }
}

# ─── 5. Assemble ────────────────────────────────────────────────────────────
function Join-SetupExecutable {
    param(
        [string]$Bootstrapper,
        [string]$Payload,
        [string]$Destination
    )

    if (Test-Path $Destination) {
        Remove-Item $Destination -Force
    }

    $buffer = New-Object byte[] (1024 * 1024)

    $out = [System.IO.File]::Create($Destination)
    try {
        $writer = New-Object System.IO.BinaryWriter($out)
        try {
            foreach ($part in @($Bootstrapper, $Payload)) {
                $input = [System.IO.File]::OpenRead($part)
                try {
                    while (($read = $input.Read($buffer, 0, $buffer.Length)) -gt 0) {
                        $writer.Write($buffer, 0, $read)
                    }
                }
                finally {
                    $input.Dispose()
                }
            }

            # Trailer: payload length (int64) + magic. Mirrors Setup.cs.
            $writer.Write([Int64](Get-Item $Payload).Length)
            $writer.Write([byte[]]@(0x53, 0x50, 0x53, 0x54))  # "SPST"
            $writer.Flush()
        }
        finally {
            $writer.Dispose()
        }
    }
    finally {
        $out.Dispose()
    }
}

# ─── 6. Verify ──────────────────────────────────────────────────────────────
function Test-SetupExecutable {
    param([string]$Path)

    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $length = $stream.Length
        $stream.Seek(-12, [System.IO.SeekOrigin]::End) | Out-Null
        $tail = New-Object byte[] 12
        if ($stream.Read($tail, 0, 12) -ne 12) {
            throw 'Setup trailer is truncated.'
        }

        $magic = [System.Text.Encoding]::ASCII.GetString($tail, 8, 4)
        if ($magic -ne 'SPST') {
            throw "Setup trailer magic mismatch (found '$magic')."
        }

        $payloadLength = [BitConverter]::ToInt64($tail, 0)
        $payloadOffset = $length - 12 - $payloadLength
        if ($payloadOffset -le 0) {
            throw 'Setup payload offset is invalid.'
        }

        $verificationZip = Join-Path $objDir 'verify-payload.zip'
        if (Test-Path $verificationZip) {
            Remove-Item $verificationZip -Force
        }

        $stream.Seek($payloadOffset, [System.IO.SeekOrigin]::Begin) | Out-Null
        $target = [System.IO.File]::Create($verificationZip)
        try {
            $buffer = New-Object byte[] (1024 * 1024)
            $remaining = $payloadLength
            while ($remaining -gt 0) {
                $want = [Math]::Min($buffer.Length, $remaining)
                $read = $stream.Read($buffer, 0, $want)
                if ($read -le 0) {
                    throw 'Setup payload is truncated.'
                }
                $target.Write($buffer, 0, $read)
                $remaining -= $read
            }
        }
        finally {
            $target.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($verificationZip)
    try {
        $entries = $archive.Entries
        $launcher = $entries | Where-Object {
            $_.FullName.Replace('\', '/') -eq 'StackPilot/StackPilot.exe'
        }
        if (-not $launcher) {
            throw 'Payload does not contain StackPilot/StackPilot.exe'
        }
        if (-not ($entries | Where-Object { $_.FullName.Replace('\', '/') -like 'StackPilot/runtime/*' })) {
            throw 'Payload does not contain a bundled JVM runtime.'
        }
        Write-Host ("Payload verified: {0} entries, launcher {1:N0} bytes" -f $entries.Count, $launcher.Length)
    }
    finally {
        $archive.Dispose()
        Remove-Item (Join-Path $objDir 'verify-payload.zip') -Force -ErrorAction SilentlyContinue
    }
}

# ─── Main ───────────────────────────────────────────────────────────────────
$version = Get-PackageVersion
$sourceVersion = (Select-String -Path $setupSource -Pattern 'AppVersion\s*=\s*"([^"]+)"' |
    Select-Object -First 1).Matches[0].Groups[1].Value
if ($sourceVersion -ne $version) {
    throw "Version mismatch: build.gradle.kts packageVersion=$version but Setup.cs AppVersion=$sourceVersion"
}

New-Item -ItemType Directory -Force -Path $objDir | Out-Null
New-Item -ItemType Directory -Force -Path $distDir | Out-Null

Write-Step "Generating application icon"
New-AppIcon

if (-not $SkipBuild) {
    Write-Step 'Building the desktop application image'
    Invoke-AppImageBuild
}

if (-not (Test-Path (Join-Path $appImageDir 'StackPilot.exe'))) {
    throw "Application image not found: $appImageDir. Run without -SkipBuild."
}

$payloadZip = Join-Path $objDir 'payload.zip'
if (-not $SkipPayload -or -not (Test-Path $payloadZip)) {
    Write-Step 'Creating payload archive'
    New-PayloadArchive -Destination $payloadZip
}
else {
    Write-Step 'Reusing existing payload archive'
    Write-Host "Payload: $payloadZip"
}

$bootstrapper = Join-Path $objDir 'StackPilotBootstrapper.exe'
Write-Step 'Compiling the setup bootstrapper'
New-Bootstrapper -Destination $bootstrapper

$setupExe = Join-Path $distDir "StackPilot-Setup-$version.exe"
Write-Step 'Assembling the setup executable'
Join-SetupExecutable -Bootstrapper $bootstrapper -Payload $payloadZip -Destination $setupExe

Write-Step 'Verifying the setup executable'
Test-SetupExecutable -Path $setupExe

$setupMb = [Math]::Round((Get-Item $setupExe).Length / 1MB, 1)
Write-Host ''
Write-Host "Setup executable ready: $setupExe ($setupMb MB)" -ForegroundColor Green
Write-Host 'Double-click it (approve the UAC prompt) to install StackPilot for all users.'
