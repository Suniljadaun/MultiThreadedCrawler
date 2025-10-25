# Prints machine and tool versions for the "Environment" section of a benchmark report
$ErrorActionPreference = "Continue"

function First-Line($block) { try { "$(& $block 2>&1 | Select-Object -First 1)".Trim() } catch { "n/a" } }

$os = Get-CimInstance Win32_OperatingSystem
$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
"Date:      $(Get-Date -Format o)"
"OS:        $($os.Caption) $($os.Version)"
"CPU:       $($cpu.Name.Trim()) ($($cpu.NumberOfCores) cores, $($cpu.NumberOfLogicalProcessors) threads)"
"RAM:       {0:N1} GB" -f ($os.TotalVisibleMemorySize / 1MB)
"Java:      $(First-Line { java -version })"
"Python:    $(First-Line { python --version })"
$k6 = (Get-Command k6 -ErrorAction SilentlyContinue).Source
if (-not $k6) { $k6 = "$env:ProgramFiles\k6\k6.exe" }
"k6:        $(First-Line { & $k6 version })"
"Docker:    $(First-Line { docker version --format '{{.Server.Version}}' })"
"Docker VM: $(First-Line { docker info --format '{{.NCPU}} CPUs, {{.MemTotal}} bytes' })"
"Postgres:  $(First-Line { docker exec finintel-postgres psql -U finintel -d finintel -tAc 'SHOW server_version' })"
"Git:       $(First-Line { git rev-parse --short HEAD }) (uncommitted files: $((git status --porcelain | Measure-Object).Count))"
