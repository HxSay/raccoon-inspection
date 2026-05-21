<#
.SYNOPSIS
  Connect to Milvus without the interactive REPL (works in Cursor / PowerShell).

.EXAMPLE
  .\scripts\milvus-connect.ps1
  .\scripts\milvus-connect.ps1 -Uri http://127.0.0.1:19530
#>
param(
    [string]$Uri = 'http://127.0.0.1:19530'
)

$ErrorActionPreference = 'Stop'
$pyScripts = Join-Path $env:LOCALAPPDATA 'Programs\Python\Python312\Scripts'
if (Test-Path $pyScripts) {
    $env:Path = "$pyScripts;$env:Path"
}

python -c @"
from milvus_cli.scripts.milvus_cli import cli
cli(['connect', '-uri', '$Uri'], standalone_mode=True)
"@
