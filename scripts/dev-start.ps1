<#
.SYNOPSIS
  Start Raccoon local dev environment (microservices + raccoon-ui).
  If a service port is already in use, the listener process is stopped first, then the service starts.

.PARAMETER Profile
  Core = system, agent, drone, iot-data + frontend
  All  = all cloud services + frontend (default)

.PARAMETER Compile
  Run mvn compile before starting.

.PARAMETER NoFrontend
  Skip raccoon-ui.

.PARAMETER NoMobile
  Skip raccoon-mobile (port 5174).

.PARAMETER NoDroneSim
  Skip raccoon-drone-sim (port 3010). Main UI embed still needs it for /sim/drone.

.PARAMETER ShowWindows
  Open one PowerShell window per service (old behavior).

.EXAMPLE
  .\scripts\dev-start.ps1
  .\scripts\dev-start.ps1 -Profile Core
  .\scripts\dev-start.ps1 -ShowWindows
#>
param(
    [ValidateSet('Core', 'All')]
    [string]$Profile = 'All',
    [switch]$Compile,
    [switch]$NoFrontend,
    [switch]$NoMobile,
    [switch]$NoDroneSim,
    [switch]$ShowWindows,
    [int]$StaggerSeconds = 4
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $Root

function Write-Info([string]$msg)  { Write-Host $msg -ForegroundColor Cyan }
function Write-Ok([string]$msg)    { Write-Host $msg -ForegroundColor Green }
function Write-WarnMsg([string]$msg) { Write-Host $msg -ForegroundColor Yellow }

function Resolve-Maven {
    $candidates = @(
        $env:MAVEN_HOME,
        'C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.2\plugins\maven\lib\maven3',
        'C:\Program Files\JetBrains\IntelliJ IDEA\plugins\maven\lib\maven3'
    )
    foreach ($c in $candidates) {
        if (-not $c) { continue }
        $path = if ($c -like '*mvn.cmd') { $c } else { Join-Path $c 'bin\mvn.cmd' }
        if (Test-Path $path) { return $path }
    }
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'Maven not found. Install Maven or set MAVEN_HOME.'
}

function Resolve-JavaHome {
    $candidates = @(
        $env:JAVA_HOME,
        'C:\Program Files\Java\jdk-17'
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c 'bin\java.exe'))) {
            return $c
        }
    }
    $adoptium = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($adoptium) { return $adoptium.FullName }
    throw 'JDK 17 not found. Set JAVA_HOME to JDK 17.'
}

function Resolve-Npm {
    $cmd = Get-Command npm -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'npm not found. Install Node.js first.'
}

function Test-PortListening([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $conn
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
        $lines = netstat -ano | Select-String ":\s*$Port\s+.*LISTENING"
        foreach ($line in $lines) {
            $parts = ($line -replace '\s+', ' ').ToString().Trim().Split(' ')
            $procId = [int]$parts[-1]
            if ($procId -gt 0) { $result += $procId }
        }
    }
    return $result | Select-Object -Unique
}

function Clear-PortListener {
    param([int]$Port, [string]$Label)

    if (-not (Test-PortListening $Port)) { return $true }

    Write-WarnMsg "[busy] $Label port $Port in use, stopping listener..."
    $pids = Get-ListenersOnPort -Port $Port
    if (-not $pids) {
        Write-WarnMsg "[skip] $Label port $Port still busy (could not resolve PID)."
        return $false
    }
    foreach ($procId in $pids) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        $pname = if ($proc) { $proc.ProcessName } else { 'unknown' }
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Ok "[stop] $Label port $Port -> PID $procId ($pname)"
    }
    Start-Sleep -Seconds 2
    if (Test-PortListening $Port) {
        Write-WarnMsg "[skip] $Label port $Port still in use after stop."
        return $false
    }
    return $true
}

function Escape-SingleQuoted([string]$Value) {
    if ($null -eq $Value) { return '' }
    $q = [char]39
    return $Value.Replace([string]$q, [string]$q + [string]$q)
}

function Get-RunDir {
    $dir = Join-Path $PSScriptRoot '.run'
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
    return $dir
}

function Get-LogDir {
    $dir = Join-Path $PSScriptRoot 'logs'
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
    return $dir
}

# 停止仍占用日志的旧 launcher（隐藏 PowerShell 父进程）
function Stop-StaleServiceLauncher([string]$Name) {
    $launcher = Join-Path (Get-RunDir) "start-$Name.ps1"
    if (-not (Test-Path $launcher)) { return }
    $launcherPath = (Resolve-Path $launcher).Path
    $escaped = [regex]::Escape($launcherPath)
    try {
        Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -and ($_.CommandLine -match $escaped) } |
            ForEach-Object {
                Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
                Write-Ok "[stop] stale launcher $Name -> PID $($_.ProcessId)"
            }
    } catch {
        # Win32_Process 不可用时忽略
    }
    Start-Sleep -Milliseconds 800
}

# 清空日志；文件被占用时轮转备份，避免 Set-Content 报错中断启动
function Clear-LogFileSafe([string]$Path) {
    $dir = Split-Path $Path -Parent
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
    if (-not (Test-Path $Path)) {
        New-Item -ItemType File -Path $Path -Force | Out-Null
        return
    }
    for ($i = 0; $i -lt 6; $i++) {
        try {
            $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
            $fs.Close()
            return
        } catch {
            Start-Sleep -Milliseconds 400
        }
    }
    $bak = "$Path.bak.$((Get-Date).ToString('yyyyMMddHHmmss'))"
    try {
        Move-Item -LiteralPath $Path -Destination $bak -Force
        New-Item -ItemType File -Path $Path -Force | Out-Null
        Write-WarnMsg "[warn] log locked, rotated -> $bak"
    } catch {
        Write-WarnMsg "[warn] cannot clear log $Path : $($_.Exception.Message). Output may append."
    }
}

function Write-LauncherScript([string]$Path, [string[]]$Lines) {
    $text = ($Lines -join [Environment]::NewLine) + [Environment]::NewLine
    [System.IO.File]::WriteAllText($Path, $text, (New-Object System.Text.UTF8Encoding $true))
}

function Start-LauncherProcess {
    param(
        [string]$LauncherPath,
        [string]$Name,
        [bool]$VisibleWindow
    )
    if ($VisibleWindow) {
        Start-Process -FilePath 'powershell.exe' -ArgumentList @(
            '-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $LauncherPath
        ) -WindowStyle Normal | Out-Null
        return $null
    }

    $logDir = Get-LogDir
    $outLog = Join-Path $logDir "$Name.log"
    $errLog = Join-Path $logDir "$Name.err.log"
    Clear-LogFileSafe -Path $outLog
    Clear-LogFileSafe -Path $errLog

    $proc = Start-Process -FilePath 'powershell.exe' -PassThru -WindowStyle Hidden -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $LauncherPath
    ) -RedirectStandardOutput $outLog -RedirectStandardError $errLog

    return @{
        Pid    = $proc.Id
        OutLog = $outLog
        ErrLog = $errLog
    }
}

function Start-SpringService {
    param(
        [string]$Name,
        [string]$Module,
        [int]$Port,
        [string]$Mvn,
        [string]$JavaHome,
        [string]$RootPath,
        [bool]$VisibleWindow
    )
    if (-not (Clear-PortListener -Port $Port -Label $Name)) { return }
    Stop-StaleServiceLauncher -Name $Name
    $pomPath = Join-Path $RootPath 'pom.xml'
    $launcher = Join-Path (Get-RunDir) "start-$Name.ps1"
    $mvnEsc = Escape-SingleQuoted $Mvn
    $pomEsc = Escape-SingleQuoted $pomPath
    $packageCmd = "& '$mvnEsc' -f '$pomEsc' -pl '$Module' -am -DskipTests package"
    $runCmd = "& '$mvnEsc' -f '$pomEsc' -pl '$Module' -DskipTests spring-boot:run"
    Write-LauncherScript -Path $launcher -Lines @(
        ('$env:JAVA_HOME = ''{0}''' -f (Escape-SingleQuoted $JavaHome))
        ('Set-Location -LiteralPath ''{0}''' -f (Escape-SingleQuoted $RootPath))
        "Write-Output '>>> Starting $Name (port $Port)...'"
        $packageCmd
        'if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }'
        $runCmd
    )
    $info = Start-LauncherProcess -LauncherPath $launcher -Name $Name -VisibleWindow $VisibleWindow
    if ($info) {
        Write-Ok "[start] $Name -> http://localhost:$Port  (PID $($info.Pid), log: $($info.OutLog))"
    } else {
        Write-Ok "[start] $Name -> http://localhost:$Port  (window)"
    }
}

function Start-DroneSim {
    param(
        [string]$Npm,
        [string]$RootPath,
        [bool]$VisibleWindow
    )
    $simDir = Join-Path $RootPath 'raccoon-drone-sim'
    if (-not (Test-Path (Join-Path $simDir 'package.json'))) {
        Write-WarnMsg '[skip] raccoon-drone-sim not found'
        return
    }
    if (-not (Clear-PortListener -Port 3010 -Label 'drone-sim')) { return }
    Stop-StaleServiceLauncher -Name 'drone-sim'
    $launcher = Join-Path (Get-RunDir) 'start-drone-sim.ps1'
    $npmCmd = "& '$((Escape-SingleQuoted $Npm))' run dev"
    Write-LauncherScript -Path $launcher -Lines @(
        ('Set-Location -LiteralPath ''{0}''' -f (Escape-SingleQuoted $simDir))
        "Write-Output '>>> Starting raccoon-drone-sim http://localhost:3010 ...'"
        $npmCmd
    )
    $info = Start-LauncherProcess -LauncherPath $launcher -Name 'drone-sim' -VisibleWindow $VisibleWindow
    if ($info) {
        Write-Ok "[start] raccoon-drone-sim -> http://localhost:3010  (PID $($info.Pid), log: $($info.OutLog))"
    } else {
        Write-Ok '[start] raccoon-drone-sim -> http://localhost:3010  (window)'
    }
}

function Start-Mobile {
    param(
        [string]$Npm,
        [string]$RootPath,
        [bool]$VisibleWindow
    )
    $mobileDir = Join-Path $RootPath 'raccoon-mobile'
    if (-not (Test-Path (Join-Path $mobileDir 'package.json'))) {
        Write-WarnMsg '[skip] raccoon-mobile not found'
        return
    }
    if (-not (Clear-PortListener -Port 5174 -Label 'mobile')) { return }
    Stop-StaleServiceLauncher -Name 'mobile'
    $launcher = Join-Path (Get-RunDir) 'start-mobile.ps1'
    $npmCmd = "& '$((Escape-SingleQuoted $Npm))' run dev"
    Write-LauncherScript -Path $launcher -Lines @(
        ('Set-Location -LiteralPath ''{0}''' -f (Escape-SingleQuoted $mobileDir))
        "Write-Output '>>> Starting raccoon-mobile http://localhost:5174 ...'"
        $npmCmd
    )
    $info = Start-LauncherProcess -LauncherPath $launcher -Name 'mobile' -VisibleWindow $VisibleWindow
    if ($info) {
        Write-Ok "[start] raccoon-mobile -> http://localhost:5174  (PID $($info.Pid), log: $($info.OutLog))"
    } else {
        Write-Ok '[start] raccoon-mobile -> http://localhost:5174  (window)'
    }
}

function Start-Frontend {
    param(
        [string]$Npm,
        [string]$RootPath,
        [bool]$VisibleWindow
    )
    $uiDir = Join-Path $RootPath 'raccoon-ui'
    if (-not (Test-Path (Join-Path $uiDir 'package.json'))) {
        throw "raccoon-ui not found: $uiDir"
    }
    if (-not (Clear-PortListener -Port 3000 -Label 'raccoon-ui')) { return }
    Stop-StaleServiceLauncher -Name 'ui'
    $launcher = Join-Path (Get-RunDir) 'start-ui.ps1'
    $npmCmd = "& '$((Escape-SingleQuoted $Npm))' run dev"
    Write-LauncherScript -Path $launcher -Lines @(
        ('Set-Location -LiteralPath ''{0}''' -f (Escape-SingleQuoted $uiDir))
        "Write-Output '>>> Starting raccoon-ui http://localhost:3000 ...'"
        $npmCmd
    )
    $info = Start-LauncherProcess -LauncherPath $launcher -Name 'ui' -VisibleWindow $VisibleWindow
    if ($info) {
        Write-Ok "[start] raccoon-ui -> http://localhost:3000  (PID $($info.Pid), log: $($info.OutLog))"
    } else {
        Write-Ok '[start] raccoon-ui -> http://localhost:3000  (window)'
    }
}

$AllServices = @(
    @{ Name = 'gateway';   Module = 'raccoon-cloud/raccoon-cloud-gateway';   Port = 8080 }
    @{ Name = 'agent';     Module = 'raccoon-cloud/raccoon-cloud-agent';     Port = 8081 }
    @{ Name = 'knowledge'; Module = 'raccoon-cloud/raccoon-cloud-knowledge'; Port = 8082 }
    @{ Name = 'data';      Module = 'raccoon-cloud/raccoon-cloud-data';      Port = 8083 }
    @{ Name = 'workflow';  Module = 'raccoon-cloud/raccoon-cloud-workflow';  Port = 8084 }
    @{ Name = 'device';    Module = 'raccoon-cloud/raccoon-cloud-device';    Port = 8085 }
    @{ Name = 'ai-model';  Module = 'raccoon-cloud/raccoon-cloud-ai-model';  Port = 8086 }
    @{ Name = 'system';    Module = 'raccoon-cloud/raccoon-cloud-system';    Port = 8087 }
    @{ Name = 'drone';     Module = 'raccoon-cloud/raccoon-cloud-drone';     Port = 8091 }
    @{ Name = 'iot-data';  Module = 'raccoon-cloud/raccoon-cloud-iot-data';  Port = 8092 }
)

$CoreModules = @('system', 'agent', 'drone', 'iot-data')
$Services = if ($Profile -eq 'Core') {
    $AllServices | Where-Object { $CoreModules -contains $_.Name }
} else {
    $AllServices
}

$gateway = $Services | Where-Object { $_.Name -eq 'gateway' }
$others = $Services | Where-Object { $_.Name -ne 'gateway' } | Sort-Object Port
$Services = @($gateway) + @($others) | Where-Object { $_ }

$mvn = Resolve-Maven
$javaHome = Resolve-JavaHome
$env:JAVA_HOME = $javaHome
$visible = [bool]$ShowWindows

Write-Host ''
Write-Host '========================================' -ForegroundColor DarkCyan
Write-Host '  Raccoon dev environment' -ForegroundColor DarkCyan
Write-Host "  Profile=$Profile  Compile=$Compile  Frontend=$(-not $NoFrontend)  Mobile=$(-not $NoMobile)  DroneSim=$(-not $NoDroneSim)  Windows=$visible" -ForegroundColor DarkCyan
Write-Host '========================================' -ForegroundColor DarkCyan
Write-Host ''
Write-WarnMsg 'Prerequisites (not started by this script):'
Write-Host '  MySQL 3306 | Redis 6379 | Nacos 8848' -ForegroundColor DarkGray
Write-Host ''
Write-Host "Maven: $mvn" -ForegroundColor DarkGray
Write-Host "Java : $javaHome" -ForegroundColor DarkGray
if (-not $visible) {
    Write-Host "Logs : $(Get-LogDir)" -ForegroundColor DarkGray
}
Write-Host ''

if ($Compile) {
    $modules = ($Services | ForEach-Object { $_.Module }) -join ','
    Write-Info ">>> mvn compile: $modules"
    & $mvn -f "$Root\pom.xml" -pl $modules -am compile -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven compile failed: exit $LASTEXITCODE" }
    Write-Ok '>>> compile done'
    Write-Host ''
}

foreach ($svc in $Services) {
    Start-SpringService -Name $svc.Name -Module $svc.Module -Port $svc.Port `
        -Mvn $mvn -JavaHome $javaHome -RootPath $Root -VisibleWindow $visible
    if ($StaggerSeconds -gt 0) { Start-Sleep -Seconds $StaggerSeconds }
}

if (-not $NoFrontend -or -not $NoMobile -or -not $NoDroneSim) {
    Start-Sleep -Seconds 2
    $npm = Resolve-Npm
    if (-not $NoDroneSim) {
        Start-DroneSim -Npm $npm -RootPath $Root -VisibleWindow $visible
        Start-Sleep -Seconds 2
    }
    if (-not $NoFrontend) {
        Start-Frontend -Npm $npm -RootPath $Root -VisibleWindow $visible
    }
    if (-not $NoMobile) {
        Start-Mobile -Npm $npm -RootPath $Root -VisibleWindow $visible
    }
}

Write-Host ''
if ($visible) {
    Write-Ok 'Services started in separate windows.'
} else {
    Write-Ok 'Services started in background (no extra windows).'
    Write-Host ''
    Write-Host 'View logs (examples):' -ForegroundColor Cyan
    Write-Host "  Get-Content $(Join-Path (Get-LogDir) 'system.log') -Wait -Tail 40" -ForegroundColor DarkGray
    Write-Host "  Get-Content $(Join-Path (Get-LogDir) 'ui.log') -Wait -Tail 40" -ForegroundColor DarkGray
}
Write-Host ''
Write-Host '  UI (PC):     http://localhost:3000' -ForegroundColor White
Write-Host '  工单审核(PC): http://localhost:3000/cmms/work-order-audit' -ForegroundColor White
if (-not $NoMobile) {
    Write-Host '  Mobile:      http://localhost:5174  (工单审核: /audit)' -ForegroundColor White
}
if (-not $NoDroneSim) {
    Write-Host '  Drone sim (standalone): http://localhost:3010' -ForegroundColor White
    Write-Host '  Drone sim (embedded):    http://localhost:3000/sim/drone' -ForegroundColor White
}
if ($Profile -eq 'All') {
    Write-Host '  Gateway: http://localhost:8080' -ForegroundColor White
}
Write-Host ''
Write-Host 'Stop: .\scripts\dev-stop.ps1' -ForegroundColor DarkGray
Write-Host ''
