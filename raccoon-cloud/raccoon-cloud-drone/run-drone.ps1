# 用 Maven 启动 drone（不依赖 IDE classpath）
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $root

$mvnCandidates = @(
    $env:MAVEN_HOME,
    "C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.2\plugins\maven\lib\maven3\bin\mvn.cmd",
    "C:\Users\tsinghua\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd"
)
$mvn = $null
foreach ($c in $mvnCandidates) {
    if (-not $c) { continue }
    $path = if ($c -like "*mvn.cmd") { $c } else { Join-Path $c "bin\mvn.cmd" }
    if (Test-Path $path) { $mvn = $path; break }
}
if (-not $mvn) {
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { $mvn = $cmd.Source }
}
if (-not $mvn) {
    Write-Host "未找到 mvn。请安装 Maven 或配置 MAVEN_HOME。" -ForegroundColor Red
    exit 1
}

Write-Host ">>> 使用: $mvn" -ForegroundColor DarkGray
Write-Host ">>> 编译并启动 raccoon-cloud-drone (8091) ..." -ForegroundColor Cyan
& $mvn -pl raccoon-cloud/raccoon-cloud-drone -am spring-boot:run
