# Sprint 5-A 端到端测试脚本（第一次查询）
# 上传三个文件 → 向量化 → 搜索验证
$ErrorActionPreference = "Stop"
$BASE = "http://localhost:8080/api"
$TEST_DIR = "FocusOS-AI\test-files"

Write-Host "===== Sprint 5-A 测试：第一次查询 =====" -ForegroundColor Cyan

# 1. 注册用户
Write-Host "`n[1/6] 注册测试用户..." -ForegroundColor Yellow
$regBody = @{ username="test5a"; email="test5a@focusos.com"; password="123456" } | ConvertTo-Json
try {
    $regResp = Invoke-RestMethod -Uri "$BASE/auth/register" -Method Post -Body $regBody -ContentType "application/json"
    Write-Host "注册成功: $($regResp.message)"
} catch {
    $msg = $_.ErrorDetails.Message
    if ($msg -match "已存在|already") { Write-Host "用户已存在，继续登录" }
    else { Write-Host "注册响应: $msg" }
}

# 2. 登录获取 token
Write-Host "`n[2/6] 登录获取 Token..." -ForegroundColor Yellow
$loginBody = @{ username="test5a"; password="123456" } | ConvertTo-Json
$loginResp = Invoke-RestMethod -Uri "$BASE/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginResp.data.token
if (-not $token) { $token = $loginResp.data.accessToken }
if (-not $token) { $token = $loginResp.data.'token' }
Write-Host "Token 获取成功: $($token.Substring(0,20))..."
$headers = @{ Authorization = "Bearer $token" }

# 3. 上传三个文件
Write-Host "`n[3/6] 上传三个文件..." -ForegroundColor Yellow
$files = @(
    @{ path="$TEST_DIR\resume.pdf"; title="张明个人简历"; category="简历" },
    @{ path="$TEST_DIR\实习日志.txt"; title="字节跳动实习日志"; category="日志" },
    @{ path="$TEST_DIR\FocusOS项目说明.md"; title="FocusOS AI 项目说明"; category="文档" }
)
$docIds = @()
foreach ($f in $files) {
    $uploadResp = Invoke-RestMethod -Uri "$BASE/rag/documents/upload" -Method Post -Headers $headers -Form @{
        file = Get-Item $f.path
        title = $f.title
        category = $f.category
    }
    $docId = $uploadResp.data.id
    $docIds += $docId
    Write-Host "  上传成功: $($f.title) → docId=$docId"
}

# 4. 向量化三个文件
Write-Host "`n[4/6] 向量化三个文件（写入 Milvus）..." -ForegroundColor Yellow
foreach ($i in 0..($docIds.Count - 1)) {
    $docId = $docIds[$i]
    $vecResp = Invoke-RestMethod -Uri "$BASE/rag/documents/$docId/vectorize" -Method Post -Headers $headers
    Write-Host "  向量化成功: $($files[$i].title) → isVectorized=$($vecResp.data.isVectorized)"
}

# 5. 搜索查询（第一次）
Write-Host "`n[5/6] 第一次搜索查询（验证 Milvus 写入）..." -ForegroundColor Yellow
$queries = @("实习经历", "FocusOS 项目技术栈", "简历技能")
foreach ($q in $queries) {
    $searchResp = Invoke-RestMethod -Uri "$BASE/rag/search?query=$([uri]::EscapeDataString($q))" -Method Get -Headers $headers
    $resultLen = if ($searchResp.data) { $searchResp.data.Length } else { 0 }
    $preview = if ($searchResp.data) { $searchResp.data.Substring(0, [Math]::Min(120, $searchResp.data.Length)) } else { "(空)" }
    Write-Host "  查询「$q」→ 命中字符数=$resultLen"
    Write-Host "    预览: $preview..." -ForegroundColor DarkGray
}

# 6. 验证用户隔离
Write-Host "`n[6/6] 验证用户隔离（注册第二个用户，查询应为空）..." -ForegroundColor Yellow
$regBody2 = @{ username="test5a_other"; email="other@focusos.com"; password="123456" } | ConvertTo-Json
try { Invoke-RestMethod -Uri "$BASE/auth/register" -Method Post -Body $regBody2 -ContentType "application/json" | Out-Null } catch {}
$loginBody2 = @{ username="test5a_other"; password="123456" } | ConvertTo-Json
$loginResp2 = Invoke-RestMethod -Uri "$BASE/auth/login" -Method Post -Body $loginBody2 -ContentType "application/json"
$token2 = $loginResp2.data.token
if (-not $token2) { $token2 = $loginResp2.data.accessToken }
$headers2 = @{ Authorization = "Bearer $token2" }
$searchResp2 = Invoke-RestMethod -Uri "$BASE/rag/search?query=$([uri]::EscapeDataString("实习经历"))" -Method Get -Headers $headers2
$isolated = if (-not $searchResp2.data -or $searchResp2.data.Length -eq 0) { "通过（无数据泄漏）" } else { "失败（数据泄漏！）" }
Write-Host "  其他用户查询「实习经历」→ $isolated"

# 输出文档列表
Write-Host "`n--- 文档列表 ---" -ForegroundColor Cyan
$docList = Invoke-RestMethod -Uri "$BASE/rag/documents" -Method Get -Headers $headers
foreach ($d in $docList.data) {
    Write-Host "  docId=$($d.id) title=$($d.title) vectorized=$($d.isVectorized) type=$($d.fileType)"
}

Write-Host "`n===== 第一次查询完成 =====" -ForegroundColor Green
Write-Host "文档ID列表（供重启后验证用）: $($docIds -join ', ')" -ForegroundColor Cyan
