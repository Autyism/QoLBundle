# Starts the dev client with the self-test switched on. This blocks until the game closes,
# so ALWAYS run it in the background, then run .\scripts\selftest-wait.ps1 to wait for the result.
#
#   .\scripts\selftest-start.ps1            main mod + X-ray add-on (the normal run)
#   .\scripts\selftest-start.ps1 -NoAddon   main mod alone, as someone without the add-on has it
param([switch]$NoAddon)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# The old log still contains the previous run's "[SelfTest] DONE"; remove it so the wait script
# cannot mistake it for the new run's.
$log = Join-Path $root "run\logs\latest.log"
if (Test-Path $log) { Remove-Item $log -Force -Confirm:$false }

if ($NoAddon) {
    & "$root\gradlew.bat" runClient -Pselftest -PnoAddon --console=plain
} else {
    & "$root\gradlew.bat" runClient -Pselftest --console=plain
}
