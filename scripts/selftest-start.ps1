# Starts the dev client with the self-test switched on. This blocks until the game closes,
# so ALWAYS run it in the background, then run .\scripts\selftest-wait.ps1 to wait for the result.
#
#   .\scripts\selftest-start.ps1                      everything: main mod + X-ray add-on (run this before a commit)
#   .\scripts\selftest-start.ps1 -Only "freecam,xray"  just these scenarios (quick check while working on a module)
#   .\scripts\selftest-start.ps1 -NoAddon             main mod alone, as someone without the add-on has it
param([switch]$NoAddon, [string]$Only = "")

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# The old log still contains the previous run's "[SelfTest] DONE"; remove it so the wait script
# cannot mistake it for the new run's.
$log = Join-Path $root "run\logs\latest.log"
if (Test-Path $log) { Remove-Item $log -Force -Confirm:$false }

$gradleArgs = @("runClient", "--console=plain")
if ($Only) { $gradleArgs += "-Pselftest=$Only" } else { $gradleArgs += "-Pselftest" }
if ($NoAddon) { $gradleArgs += "-PnoAddon" }
& "$root\gradlew.bat" @gradleArgs
