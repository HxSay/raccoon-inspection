<#
.SYNOPSIS
  Stop all Raccoon local dev services (microservices, UI, optional sim).

.PARAMETER Profile
  Core = system, agent, drone, iot-data + raccoon-ui
  All  = all cloud services + raccoon-ui + raccoon-drone-sim (default)

.PARAMETER KeepFrontend
  Do not stop raccoon-ui (port 3000).

.PARAMETER IncludeSim
  Also stop raccoon-drone-sim (port 3010). Default: true when Profile=All.

.PARAMETER KillJava
  Also kill java.exe whose command line contains this project path (default: true).

.EXAMPLE
  .\scripts\dev-stop.ps1
  .\scripts\dev-stop.ps1 -Profile Core
  .\scripts\dev-stop.ps1 -KillJava:$false
#>
param(
    [ValidateSet('Core', 'All')]
    [string]$Profile = 'All',
    [switch]$KeepFrontend,
    [switch]$IncludeSim,
    [bool]$KillJava = $true
)

$ErrorActionPreference = 'SilentlyContinue'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

if (-not $PSBoundParameters.ContainsKey('IncludeSim')) {
    $IncludeSim = ($Profile -eq 'All')
}

# Application ports started by dev-start.ps1 (NOT MySQL/Redis/Nacos/MinIO/Ollama)
$PortMap = [ordered]@{
    gateway   = 8080
    agent     = 8081
    knowledge = 8082
    data      = 8083
    workflow  = 8084
    device    = 8085
    'ai-model' = 8086
    system    = 8087
    drone     = 8091
    'iot-data' = 8092
    ui        = 3000
    'drone-sim' = 3010
}

$CoreNames = @('system', 'agent', 'drone', 'iot-data', 'ui')

function Write-Title([string]$msg) {
    Write-Host ''
    Write-Host '========================================' -ForegroundColor DarkCyan
    Write-Host "  $msg" -ForegroundColor DarkCyan
    Write-Host '========================================' -ForegroundColor DarkCyan
    Write-Host ''
}

function Get-ListenersOnPort([int]$Port) {
    $result = @()
    try {
        $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        foreach ($c in $conns) {
            if ($c.OwningProcess -and $c.OwningProcess -gt 0) {
                $result += [int]$c.OwningProcess
            }
        }
    } catch {
        # fallback: netstat
        $lines = netstat -ano | Select-String ":\s*$Port\s+.*LISTENING"
        foreach ($line in $lines) {
            $parts = ($line -replace '\s+', ' ').ToString().Trim().Split(' ')
            $procId = [int]$parts[-1]
            if ($procId -gt 0) { $result += $procId }
        }
    }
    return $result | Select-Object -Unique
}

function Stop-PortProcess {
    param([int]$Port, [string]$Label)

    $pids = Get-ListenersOnPort -Port $Port
    if (-not $pids) {
        Write-Host "[--] $Label (port $Port) not running" -ForegroundColor DarkGray
        return 0
    }

    $stopped = 0
    foreach ($procId in $pids) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        $name = if ($proc) { $proc.ProcessName } else { 'unknown' }
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Host "[OK] stopped $Label (port $Port, PID $procId, $name)" -ForegroundColor Green
        $stopped++
    }
    return $stopped
}

function Stop-RaccoonJavaProcesses {
    $rootEsc = [regex]::Escape($Root)
    $procs = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match $rootEsc) -and ($_.CommandLine -match 'raccoon-cloud|spring-boot|maven') }

    if (-not $procs) {
        Write-Host '[--] no leftover java.exe for this project' -ForegroundColor DarkGray
        return 0
    }

    $count = 0
    foreach ($p in $procs) {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host "[OK] killed java PID $($p.ProcessId)" -ForegroundColor Green
        $count++
    }
    return $count
}

function Stop-RaccoonNodeProcesses {
    $uiPath = [regex]::Escape((Join-Path $Root 'raccoon-ui'))
    $simPath = [regex]::Escape((Join-Path $Root 'raccoon-drone-sim'))
    $procs = Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" -ErrorAction SilentlyContinue |
        Where-Object {
            $_.CommandLine -and (
                ($_.CommandLine -match $uiPath) -or
                ($_.CommandLine -match $simPath) -or
                ($_.CommandLine -match 'vite')
            )
        }

    if (-not $procs) {
        Write-Host '[--] no raccoon-ui / drone-sim node process' -ForegroundColor DarkGray
        return 0
    }

    $count = 0
    foreach ($p in $procs) {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host "[OK] killed node PID $($p.ProcessId)" -ForegroundColor Green
        $count++
    }
    return $count
}

function Stop-HiddenLauncherShells {
    $runDir = Join-Path $PSScriptRoot '.run'
    if (-not (Test-Path $runDir)) { return 0 }
    $count = 0
    Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        $cmd = $_.CommandLine
        if ($cmd -and $cmd -like "*$runDir\start-*.ps1*") {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
            Write-Host "[OK] stopped launcher shell PID $($_.ProcessId)" -ForegroundColor Green
            $count++
        }
    }
    if ($count -eq 0) {
        Write-Host '[--] no background launcher shells' -ForegroundColor DarkGray
    }
    return $count
}

function Close-RaccoonPowerShellWindows {
    $closed = 0
    Get-Process powershell -ErrorAction SilentlyContinue | ForEach-Object {
        try {
            $title = $_.MainWindowTitle
            if ($title -and $title -like 'Raccoon-*') {
                Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
                Write-Host "[OK] closed window: $title" -ForegroundColor Green
                $closed++
            }
        } catch { }
    }
    if ($closed -eq 0) {
        Write-Host '[--] no Raccoon-* PowerShell windows' -ForegroundColor DarkGray
    }
}

# ---------- build stop list ----------
$toStop = if ($Profile -eq 'Core') {
    $CoreNames
} else {
    @($PortMap.Keys)
}

if ($KeepFrontend) {
    $toStop = $toStop | Where-Object { $_ -ne 'ui' }
}
if (-not $IncludeSim) {
    $toStop = $toStop | Where-Object { $_ -ne 'drone-sim' }
}

Write-Title "Raccoon stop (Profile=$Profile)"

$portStopped = 0
foreach ($name in $toStop) {
    if (-not $PortMap.Contains($name)) { continue }
    $portStopped += Stop-PortProcess -Port $PortMap[$name] -Label $name
}

Write-Host ''
Write-Host '>>> cleanup leftover processes ...' -ForegroundColor Cyan

Stop-HiddenLauncherShells | Out-Null
if ($KillJava) {
    Stop-RaccoonJavaProcesses | Out-Null
}
Stop-RaccoonNodeProcesses | Out-Null
Close-RaccoonPowerShellWindows

Write-Host ''
Write-Host ">>> done. ports released: $portStopped" -ForegroundColor Yellow
Write-Host '    (MySQL/Redis/Nacos/MinIO/Ollama/Neo4j are NOT stopped)' -ForegroundColor DarkGray
Write-Host ''
