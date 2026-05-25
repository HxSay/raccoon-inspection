# 将 Milvus 测试页注册到前端路由与侧栏（仅当尚未集成时执行）
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$routerFile = Join-Path $Root 'raccoon-ui\src\router\index.ts'
$sidebarFile = Join-Path $Root 'raccoon-ui\src\layouts\Sidebar.vue'

if (-not (Test-Path $routerFile)) { throw "未找到 $routerFile" }

$routerText = Get-Content $routerFile -Raw -Encoding UTF8
if ($routerText -notmatch '/ai/milvus/test') {
    $routeBlock = @"
      {
        path: '/ai/milvus/test',
        name: 'MilvusTest',
        component: () => import('@/views/ai/milvus-test/index.vue'),
        meta: { title: 'Milvus 测试', icon: 'Coin' }
      },
"@
    $routerText = $routerText -replace '(\s*\{\s*path: ''/test/minio'')', ($routeBlock + '$1')
    [System.IO.File]::WriteAllText($routerFile, $routerText, (New-Object System.Text.UTF8Encoding $true))
    Write-Host "[OK] router/index.ts 已添加 /ai/milvus/test" -ForegroundColor Green
} else {
    Write-Host "[--] 路由已存在，跳过" -ForegroundColor DarkGray
}

if (Test-Path $sidebarFile) {
    $sidebarText = Get-Content $sidebarFile -Raw -Encoding UTF8
    if ($sidebarText -notmatch '/ai/milvus/test') {
        $menuLine = "      { path: '/ai/milvus/test', title: 'Milvus 测试', icon: 'Coin' },"
        $sidebarText = $sidebarText -replace "(\{ path: '/test/minio', title: 'MinIO 测试', icon: 'Upload' \})", ($menuLine + "`n      `$1")
        [System.IO.File]::WriteAllText($sidebarFile, $sidebarText, (New-Object System.Text.UTF8Encoding $true))
        Write-Host "[OK] Sidebar.vue 已添加 Milvus 测试菜单" -ForegroundColor Green
    } else {
        Write-Host "[--] 侧栏已存在，跳过" -ForegroundColor DarkGray
    }
}

Write-Host ''
Write-Host '完成。请重启 raccoon-ui 开发服务后访问 http://localhost:3000/ai/milvus/test' -ForegroundColor Cyan
