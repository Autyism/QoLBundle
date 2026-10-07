# Waits for a self-test run to finish and prints the result.
#
# Usage (from the project root):
#   1. In the BACKGROUND:  .\scripts\selftest-start.ps1
#   2. Then:               .\scripts\selftest-wait.ps1
#   For another Minecraft version pass the same -Version to both (default 1.21.11).
#
# Ends when the log contains "[SelfTest] DONE", when the dev client exits without it (crash),
# or after the timeout (13 minutes). On timeout only this project's dev client is stopped;
# no other java process (the Minecraft server, Gradle daemons) is ever touched.
param([int]$TimeoutSeconds = 780, [string]$Version = "1.21.11")

$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root "run\$Version"
$log = Join-Path $runDir "logs\latest.log"
$crashDir = Join-Path $runDir "crash-reports"
$shotDir = Join-Path $runDir "screenshots\selftest"
$start = Get-Date

function Get-DevClient {
    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
        Where-Object { $_.CommandLine -match 'QoLBundle' -and $_.CommandLine -match 'devlaunchinjector' }
}

$result = "TIMEOUT"
$seenClient = $false
while (((Get-Date) - $start).TotalSeconds -lt $TimeoutSeconds) {
    Start-Sleep -Seconds 5
    if ((Test-Path $log) -and (Select-String -Path $log -Pattern '\[SelfTest\] DONE' -Quiet)) {
        $result = "DONE"
        break
    }
    if (Get-DevClient) {
        $seenClient = $true
    } elseif ($seenClient) {
        $result = "CLIENT EXITED WITHOUT DONE"
        break
    } elseif (((Get-Date) - $start).TotalSeconds -gt 150) {
        $result = "CLIENT NEVER STARTED"
        break
    }
}

# Give the client a moment to shut down by itself, then make sure it is gone.
$waited = 0
while ((Get-DevClient) -and $waited -lt 40 -and $result -eq "DONE") {
    Start-Sleep -Seconds 2
    $waited += 2
}
$left = Get-DevClient
if ($left) {
    $left | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
    Start-Sleep -Seconds 2
    "NOTE: dev client was still running and had to be stopped (pid $($left.ProcessId -join ', '))"
}

"RESULT: $result  (after $([int]((Get-Date) - $start).TotalSeconds) s)"
if (Test-Path $log) {
    Select-String -Path $log -Pattern '\[SelfTest\]' | ForEach-Object {
        $line = $_.Line
        if ($line.Length -gt 400) { $line.Substring(0, 400) } else { $line }
    }
    $errors = Select-String -Path $log -Pattern '/ERROR\]|/FATAL\]' |
        Where-Object { $_.Line -notmatch 'SelfTest|Failed to fetch user properties|Realms|authlib' }
    if ($errors) {
        "--- other ERROR lines in the log (first 15) ---"
        $errors | Select-Object -First 15 | ForEach-Object {
            $line = $_.Line
            if ($line.Length -gt 300) { $line.Substring(0, 300) } else { $line }
        }
    }
} else {
    "no log file at $log"
}
if (Test-Path $crashDir) {
    $crashes = Get-ChildItem $crashDir -File | Where-Object { $_.LastWriteTime -gt $start.AddMinutes(-2) }
    if ($crashes) {
        "--- NEW CRASH REPORTS ---"
        $crashes | ForEach-Object { $_.FullName }
    }
}
if (Test-Path $shotDir) {
    "--- screenshots ---"
    Get-ChildItem $shotDir -Filter *.png | Sort-Object Name | ForEach-Object { "$($_.FullName)  ($([int]($_.Length / 1KB)) KB)" }
}
if (Get-DevClient) { "DEV CLIENT STILL RUNNING" } else { "dev client java process: exited" }
