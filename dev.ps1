<#
  一键启动/停止本地开发环境（Spring Boot 后端 + Vite 前端）。
  数据库使用本机 MySQL 8.0 服务（localhost:3306），配置读取项目根目录的 .env。

  用法：
    .\dev.ps1         启动
    .\dev.ps1 -Stop   停止
#>
[CmdletBinding()]
param([switch]$Stop)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $root 'backend'
$frontendDir = Join-Path $root 'frontend'
$jar = Join-Path $backendDir 'target\tech-blog-backend-0.1.0-SNAPSHOT.jar'
$pidFile = Join-Path $root '.local\dev.pids'

function Read-DotEnv {
  param([string]$Path)
  $map = @{}
  if (-not (Test-Path -LiteralPath $Path)) { return $map }
  foreach ($line in Get-Content -LiteralPath $Path) {
    $text = $line.Trim()
    if ($text.Length -eq 0 -or $text.StartsWith('#')) { continue }
    $idx = $text.IndexOf('=')
    if ($idx -lt 1) { continue }
    $map[$text.Substring(0, $idx).Trim()] = $text.Substring($idx + 1).Trim().Trim('"')
  }
  return $map
}

function Get-Setting {
  param($Map, [string]$Key, [string]$Default)
  if ($Map.ContainsKey($Key) -and $Map[$Key]) { return $Map[$Key] }
  return $Default
}

function Test-Port {
  param([int]$Port)
  $client = New-Object System.Net.Sockets.TcpClient
  try { $client.Connect('127.0.0.1', $Port); return $true }
  catch { return $false }
  finally { $client.Close() }
}

function Wait-Http {
  param([string]$Url, [int]$Seconds)
  for ($i = 0; $i -lt $Seconds; $i++) {
    try {
      $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
      if ($response.StatusCode -ge 200) { return $true }
    } catch { }
    Start-Sleep -Seconds 1
  }
  return $false
}

if ($Stop) {
  if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Host '没有记录到本脚本启动的进程（.local\dev.pids 不存在）。'
    return
  }
  $state = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
  foreach ($name in @('backend', 'frontend')) {
    $processId = $state.$name
    if (-not $processId) { continue }
    if (-not (Get-Process -Id $processId -ErrorAction SilentlyContinue)) {
      Write-Host "跳过 $name (PID $processId)：进程已退出"
      continue
    }
    # 用 cmd 包一层，避免 taskkill 的输出在 ErrorActionPreference=Stop 下中断脚本
    & cmd.exe /c "taskkill /PID $processId /T /F >nul 2>&1"
    Write-Host "已停止 $name (PID $processId)"
  }
  Remove-Item -LiteralPath $pidFile -Force
  return
}

$config = Read-DotEnv (Join-Path $root '.env')
$dbHost = Get-Setting $config 'DB_HOST' 'localhost'
$dbPort = [int](Get-Setting $config 'DB_PORT' '3306')
$serverPort = [int](Get-Setting $config 'SERVER_PORT' '8080')

Write-Host "检查数据库 $dbHost : $dbPort ..."
if (-not (Test-Port $dbPort)) {
  Write-Warning "数据库端口 $dbPort 不可达。请先启动本机 MySQL 服务：Start-Service MySQL80（需管理员），或检查 .env 中的 DB_* 配置。"
  exit 1
}

if (Test-Port $serverPort) {
  Write-Warning "端口 $serverPort 已被占用，后端可能已在运行；跳过启动后端。"
} elseif (-not (Test-Path -LiteralPath $jar)) {
  Write-Host '未找到后端 jar，先执行 Maven 构建（首次需要下载依赖）...'
  Push-Location $backendDir
  try { .\mvnw.cmd -B -DskipTests package } finally { Pop-Location }
}

$env:DB_HOST = $dbHost
$env:DB_PORT = "$dbPort"
$env:DB_NAME = Get-Setting $config 'DB_NAME' 'tech_blog'
$env:DB_USER = Get-Setting $config 'DB_USER' 'tech_blog'
$env:DB_PASSWORD = Get-Setting $config 'DB_PASSWORD' 'tech_blog_dev_password'
$env:SERVER_PORT = "$serverPort"
$env:APP_CORS_ALLOWED_ORIGIN = Get-Setting $config 'APP_CORS_ALLOWED_ORIGIN' 'http://localhost:5173'

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $pidFile) | Out-Null
$state = @{}

if (Test-Port $serverPort) {
  $state.backend = 0
} else {
  Write-Host '启动后端 ...'
  $backend = Start-Process -FilePath 'java' -ArgumentList '-jar', $jar -WorkingDirectory $backendDir -WindowStyle Normal -PassThru
  $state.backend = $backend.Id
}

if (Test-Port 5173) {
  Write-Warning '端口 5173 已被占用，前端可能已在运行；跳过启动前端。'
  $state.frontend = 0
} else {
  Write-Host '启动前端 ...'
  $frontend = Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoExit', '-Command', 'corepack pnpm dev' -WorkingDirectory $frontendDir -WindowStyle Normal -PassThru
  $state.frontend = $frontend.Id
}

$state | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8

Write-Host ''
if (Wait-Http "http://localhost:$serverPort/api/health" 60) {
  Write-Host "后端就绪：http://localhost:$serverPort/api/health"
} else {
  Write-Warning "后端 60 秒内未就绪，请查看后端窗口日志：http://localhost:$serverPort/api/health"
}
if (Wait-Http 'http://localhost:5173/' 60) {
  Write-Host '前端就绪：http://localhost:5173'
} else {
  Write-Warning '前端 60 秒内未就绪，请查看前端窗口日志。'
}
Write-Host ''
Write-Host '停止服务：.\dev.ps1 -Stop'
