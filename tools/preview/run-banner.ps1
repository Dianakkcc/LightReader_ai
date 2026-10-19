# Screenshots the chapter boundary banner while it is half pulled in, once per edge, using the same
# real Blink engine as run-preview.ps1. Usage: powershell -File run-banner.ps1
param(
    [string]$Chrome = "",
    [string]$Pages = "$PSScriptRoot\pages",
    [string]$OutDir = "$PSScriptRoot\shots"
)

if (-not $Chrome) {
    $candidates = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
    )
    $Chrome = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $Chrome -or -not (Test-Path -LiteralPath $Chrome)) {
    throw '请用 -Chrome 指定 Chrome 或 chrome-headless-shell 的可执行文件路径'
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function New-BannerPage([string]$edge) {
    $source = Join-Path $Pages 'chapter-scroll.html'
    $target = Join-Path $Pages "banner-$edge.html"
    $html = Get-Content -LiteralPath $source -Raw -Encoding UTF8
    $inject = "<pre id=""lr-harness-out"" style=""display:none""></pre>" +
        "<script>window.__lrBannerEdge = '$edge';</script>" +
        "<script src=""../harness-banner.js""></script>"
    $html = $html -replace '</body>', "$inject`n</body>"
    $html = $html -replace '<head>', "<head>`n<script src=""../pre-harness.js""></script>"
    Set-Content -LiteralPath $target -Value $html -Encoding UTF8 -NoNewline
    return $target
}

foreach ($edge in 'next', 'prev') {
    $page = New-BannerPage $edge
    $url = 'file:///' + ($page -replace '\\', '/')
    Write-Host "=== $edge ==="
    $dom = & $Chrome --headless --disable-gpu --no-sandbox --hide-scrollbars `
        --window-size=400,860 --virtual-time-budget=6000 --dump-dom $url 2>$null
    $match = [regex]::Match(($dom -join "`n"), '(?s)<pre id="lr-harness-out"[^>]*>(.*?)</pre>')
    if ($match.Success) {
        $obj = [System.Net.WebUtility]::HtmlDecode($match.Groups[1].Value) | ConvertFrom-Json
        "label      : $($obj.text)"
        "opacity    : $($obj.opacity)"
        "background : $($obj.backgroundImage)"
        "bg colour  : $($obj.backgroundColor)"
        "radius     : $($obj.borderRadius)"
        "shadow     : $($obj.boxShadow)"
        "transform  : $($obj.transform)"
        "rect       : top=$($obj.rect.top) height=$($obj.rect.height)"
        "content    : $($obj.contentTransform)"
        foreach ($e in $obj.errors) { "JS ERROR   : $e" }
        if ($obj.error) { "ERROR      : $($obj.error)" }
    } else {
        "no harness output captured"
    }

    $shot = Join-Path $OutDir "banner-$edge.png"
    & $Chrome --headless --disable-gpu --no-sandbox --hide-scrollbars `
        --window-size=400,860 --virtual-time-budget=6000 --screenshot=$shot $url 2>$null | Out-Null
    "screenshot : $shot"
    ""
}
