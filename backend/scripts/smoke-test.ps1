# =============================================================================
#  智慧材料库 · 后端接口冒烟 / 联调脚本
#
#  用法（Windows PowerShell 5.1 或 PowerShell 7）：
#     powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/smoke-test.ps1
#     pwsh -File backend/scripts/smoke-test.ps1 -Base http://127.0.0.1:8080
#
#  注意：本文件必须保存为「UTF-8 with BOM」，否则 Windows PowerShell 5.1
#        会按 GBK 读取源码里的中文导致解析错误。
#
#  前提：
#    1. PostgreSQL 15 已启动，spring.sql.init 已建表并写入种子数据；
#    2. 后端已启动（java -jar backend/target/smart-materials-server.jar）；
#    3. 数据库处于「首次初始化」状态（种子数据：stu2024001 本月已领用 6，
#       月度限额 20）。若重复执行，限额相关断言可能因已用额度过高而失败。
#
#  说明：用 curl.exe 落盘 + 显式 UTF-8 解码，避免 PowerShell 5.1
#        把 JSON 响应按 ISO-8859-1 解码导致中文乱码。
# =============================================================================

param(
    [string]$Base = 'http://127.0.0.1:8080',
    [string]$AdminUser = 'admin',
    [string]$AdminPass = '123456',
    [string]$StudentUser = 'stu2024001',
    [string]$StudentPass = '123456'
)

$ErrorActionPreference = 'Stop'
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("sm-smoke-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force $tmp | Out-Null

$script:pass = 0
$script:fail = 0

function Write-Step($title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Cyan
}

function Read-Utf8($path) {
    return [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

function Assert-That($condition, $message, $detail) {
    if ($condition) {
        $script:pass++
        Write-Host "  PASS  $message" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "  FAIL  $message" -ForegroundColor Red
        if ($detail) { Write-Host "        $detail" -ForegroundColor DarkRed }
    }
}

# 发请求：返回 @{ code; body }
function Invoke-Json {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null, [string]$Name)

    $out = Join-Path $tmp "$Name.json"
    $args = @('-s', '-w', '%{http_code}', '-o', $out, '-X', $Method)
    if ($Token) { $args += @('-H', "Authorization: Bearer $Token") }
    if ($null -ne $Body) {
        $bodyFile = Join-Path $tmp "$Name.req.json"
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Compress -Depth 6 }
        [System.IO.File]::WriteAllText($bodyFile, $json, [System.Text.UTF8Encoding]::new($false))
        $args += @('-H', 'Content-Type: application/json', '--data-binary', "@$bodyFile")
    }
    $code = (& curl.exe @args "$Base$Path").Trim()
    $raw = if (Test-Path $out) { Read-Utf8 $out } else { '' }
    $parsed = $null
    if ($raw) { try { $parsed = $raw | ConvertFrom-Json } catch { $parsed = $null } }
    return [pscustomobject]@{ code = [int]$code; body = $parsed; raw = $raw }
}

Write-Host "目标后端：$Base" -ForegroundColor Yellow

# ------------------------------------------------------------------ 1. 健康检查
Write-Step '1. GET /api/health'
$health = Invoke-Json -Method GET -Path '/api/health' -Name 'health'
Assert-That ($health.code -eq 200 -and $health.body.status -eq 'UP') `
    "health 返回 status=UP" "HTTP $($health.code) / $($health.raw)"

# ------------------------------------------------------------------ 2. 登录
Write-Step '2. POST /api/login'
$login = Invoke-Json -Method POST -Path '/api/login' -Name 'login' `
    -Body @{ username = $AdminUser; password = $AdminPass }
Assert-That ($login.code -eq 200 -and $login.body.token.Length -gt 20) `
    "admin 登录成功，返回 JWT（$($login.body.token.Length) 字符）" "HTTP $($login.code)"
$token = $login.body.token

$bad = Invoke-Json -Method POST -Path '/api/login' -Name 'login-bad' `
    -Body @{ username = $AdminUser; password = 'wrong-password' }
Assert-That ($bad.code -eq 401) "错误密码返回 401：$($bad.body.message)" "HTTP $($bad.code)"

# ------------------------------------------------------------------ 3. 受保护接口
Write-Step '3. 受保护 GET 接口（带 Bearer）'
foreach ($ep in @('/api/me', '/api/dashboard', '/api/materials', '/api/batches', '/api/alerts')) {
    $name = $ep.Trim('/').Replace('/', '-')
    $r = Invoke-Json -Method GET -Path $ep -Token $token -Name $name
    Assert-That ($r.code -eq 200) "$ep → 200" "HTTP $($r.code) / $($r.raw)"
}

$anon = Invoke-Json -Method GET -Path '/api/me' -Name 'me-anon'
Assert-That ($anon.code -eq 401) '未带 token 访问 /api/me → 401' "HTTP $($anon.code)"

# ------------------------------------------------------------------ 4. 入库
Write-Step '4. POST /api/batches（入库，生成批次号）'
$materials = Invoke-Json -Method GET -Path '/api/materials' -Token $token -Name 'materials-step4'
$hcl = $materials.body | Where-Object { $_.code -eq 'HCL-AR' } | Select-Object -First 1
$inbound = Invoke-Json -Method POST -Path '/api/batches' -Token $token -Name 'inbound' -Body @{
    itemId    = $hcl.id
    qty       = 10
    location  = 'A-01-09'
    expireDate = '2028-12-31'
    supplier  = '国药集团'
    remark    = '联调入库'
}
Assert-That ($inbound.code -eq 200) '入库成功 → 200' "HTTP $($inbound.code) / $($inbound.raw)"
Assert-That ($inbound.body.batch.batchCode -match '^IN\d{8}\d{3}$') `
    "批次号格式正确：$($inbound.body.batch.batchCode)" $inbound.body.batch.batchCode
$batchId = $inbound.body.batch.id
$batchCode = $inbound.body.batch.batchCode

# ------------------------------------------------------------------ 5. 学生限额
Write-Step '5. 学生月度限额（monthly_limit = 20，种子数据已用 6）'
$stu = Invoke-Json -Method POST -Path '/api/login' -Name 'login-stu' `
    -Body @{ username = $StudentUser; password = $StudentPass }
$stuToken = $stu.body.token
$quota = Invoke-Json -Method GET -Path '/api/stock/quota' -Token $stuToken -Name 'quota'
Assert-That ($quota.code -eq 200) `
    "quota：已用 $($quota.body.used) / 限额 $($quota.body.limit)，剩余 $($quota.body.remaining)" $quota.raw

$out1 = Invoke-Json -Method POST -Path '/api/stock/outbound' -Token $stuToken -Name 'outbound-1' -Body @{
    batchId = $batchId; qty = 10; projectNo = 'PRJ-2025-012'; usage = '联调领用'
}
Assert-That ($out1.code -eq 200) "领用 10 → 200，本月已用 $($out1.body.monthlyUsed)" "HTTP $($out1.code)"

$dishes = Invoke-Json -Method GET -Path '/api/batches' -Token $stuToken -Name 'batches-step5'
$dishItem = $materials.body | Where-Object { $_.code -eq 'DISH-90' } | Select-Object -First 1
$dishBatch = $dishes.body | Where-Object { $_.itemId -eq $dishItem.id } | Select-Object -First 1
$out2 = Invoke-Json -Method POST -Path '/api/stock/outbound' -Token $stuToken -Name 'outbound-2' -Body @{
    batchId = $dishBatch.id; qty = 10; projectNo = 'PRJ-2025-012'; usage = '超限测试'
}
Assert-That ($out2.code -eq 409 -and $out2.body.message -like '*超出个人月度限额*') `
    "超额领用 → 409：$($out2.body.message)" "HTTP $($out2.code)"

$out3 = Invoke-Json -Method POST -Path '/api/stock/outbound' -Token $stuToken -Name 'outbound-3' -Body @{
    batchId = $batchId; qty = 1; projectNo = 'PRJ-2025-012'; usage = '库存不足测试'
}
Assert-That ($out3.code -eq 409 -and $out3.body.message -like '*库存不足*') `
    "批次已空再领 → 409：$($out3.body.message)" "HTTP $($out3.code)"

# ------------------------------------------------------------------ 6. 预警开/关
Write-Step '6. 安全库存预警（低于 safe_stock 写入 OPEN，恢复后自动 CLOSED）'
$alerts = Invoke-Json -Method GET -Path '/api/alerts' -Token $token -Name 'alerts-open'
$open = $alerts.body | Where-Object { $_.itemName -like '*HCL-AR*' -and $_.status -eq 'OPEN' }
Assert-That ($null -ne $open) `
    "领用后 HCL-AR 出现 OPEN 预警（当前 $($open.currentQty) / 安全 $($open.safeStock)，级别 $($open.level)）" `
    '未找到 itemId=1 的 OPEN 预警'

$restock = Invoke-Json -Method POST -Path '/api/batches' -Token $token -Name 'inbound-restock' -Body @{
    itemId = $hcl.id; qty = 15; location = 'A-01-10'; expireDate = '2028-12-31'; remark = '补货关闭预警'
}
$alerts2 = Invoke-Json -Method GET -Path '/api/alerts' -Token $token -Name 'alerts-closed'
$stillOpen = $alerts2.body | Where-Object { $_.itemName -like '*HCL-AR*' -and $_.status -eq 'OPEN' }
Assert-That ($restock.code -eq 200 -and $null -eq $stillOpen) `
    '补货后 HCL-AR 预警自动关闭（/api/alerts 不再返回 OPEN）' "补货 HTTP $($restock.code)"

# ------------------------------------------------------------------ 7. 归还 / 报废
Write-Step '7. POST /api/stock/return-scrap（RETURN / SCRAP）'
$ret = Invoke-Json -Method POST -Path '/api/stock/return-scrap' -Token $stuToken -Name 'return' -Body @{
    type = 'RETURN'; batchId = $batchId; qty = 3; usage = '未用完归还'; projectNo = 'PRJ-2025-012'
}
Assert-That ($ret.code -eq 200 -and $ret.body.transaction.txnType -eq 'RETURN') `
    "RETURN 3 → 200，批次剩余 $($ret.body.batchQuantity)" "HTTP $($ret.code)"

$scr = Invoke-Json -Method POST -Path '/api/stock/return-scrap' -Token $stuToken -Name 'scrap' -Body @{
    type = 'SCRAP'; batchId = $batchId; qty = 1; usage = '破损报废'; projectNo = 'PRJ-2025-012'
}
Assert-That ($scr.code -eq 200 -and $scr.body.transaction.txnType -eq 'SCRAP') `
    "SCRAP 1 → 200，批次剩余 $($scr.body.batchQuantity)" "HTTP $($scr.code)"

# ------------------------------------------------------------------ 8. 批量导入 / 导出 / 附件
Write-Step '8. 批量导入 Excel / 报表导出 / 附件上传'
$tpl = Join-Path $PSScriptRoot '..\..\public\templates\materials-inbound-template.xlsx'
if (Test-Path $tpl) {
    $importOut = Join-Path $tmp 'import.json'
    $code = (& curl.exe -s -w '%{http_code}' -o $importOut -X POST -H "Authorization: Bearer $token" -F "file=@$tpl" "$Base/api/batches/import").Trim()
    Assert-That ($code -eq '200') "批量导入模板文件 → $code" (Read-Utf8 $importOut)
} else {
    Write-Host "  SKIP  未找到模板 $tpl" -ForegroundColor Yellow
}

foreach ($fmt in @('xlsx', 'pdf')) {
    $file = Join-Path $tmp "export.$fmt"
    $hdr = Join-Path $tmp "export-$fmt.h"
    $code = (& curl.exe -s -D $hdr -o $file -w '%{http_code}' -H "Authorization: Bearer $token" "$Base/api/reports/export?format=$fmt&type=OUT").Trim()
    $size = if (Test-Path $file) { (Get-Item $file).Length } else { 0 }
    $magic = if ($size -ge 4) { [System.Text.Encoding]::ASCII.GetString([System.IO.File]::ReadAllBytes($file)[0..3]) } else { '' }
    Assert-That ($code -eq '200' -and $size -gt 500) "导出 $fmt → $code（$size 字节，文件头 $magic）" "HTTP $code"
}

$png = Join-Path $tmp 'scrap.png'
$pngBytes = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==')
[System.IO.File]::WriteAllBytes($png, $pngBytes)
$attachOut = Join-Path $tmp 'attach.json'
$code = (& curl.exe -s -w '%{http_code}' -o $attachOut -X POST -H "Authorization: Bearer $token" -F "file=@$png;type=image/png" -F "category=scrap" "$Base/api/attachments").Trim()
$attach = (Read-Utf8 $attachOut) | ConvertFrom-Json
Assert-That ($code -eq '200' -and $attach.url -like '/uploads/*') "附件上传 → $code：$($attach.url)" "HTTP $code"
$codeImg = (& curl.exe -s -o (Join-Path $tmp 'attach-get.png') -w '%{http_code}' "$Base$($attach.url)").Trim()
Assert-That ($codeImg -eq '200') "附件静态访问 $($attach.url) → $codeImg" "HTTP $codeImg"

# ------------------------------------------------------------------ 汇总
Write-Step '汇总'
Write-Host ("  通过 {0} 项，失败 {1} 项" -f $script:pass, $script:fail) -ForegroundColor Yellow
Write-Host "  临时文件：$tmp"
if ($script:fail -gt 0) { exit 1 }
