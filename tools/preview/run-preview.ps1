# Runs the generated reader pages in a real Blink engine (chrome-headless-shell), measures the
# pagination and saves screenshots. Usage: powershell -File run-preview.ps1
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

function New-Harness([string]$source, [string]$target) {
    $html = Get-Content -LiteralPath $source -Raw -Encoding UTF8
    $inject = '<pre id="lr-harness-out" style="display:none"></pre><script src="../harness.js"></script>'
    $html = $html -replace '</body>', "$inject`n</body>"
    $html = $html -replace '<head>', "<head>`n<script src=""../pre-harness.js""></script>"
    Set-Content -LiteralPath $target -Value $html -Encoding UTF8 -NoNewline
}

$cases = @(
    @{ name = 'phone';      file = 'chapter-phone.html';       size = '400,860'  },
    @{ name = 'tablet-2up'; file = 'chapter-tablet-2up.html';  size = '900,1200' },
    @{ name = 'scroll';     file = 'chapter-scroll.html';      size = '400,860'  }
)

foreach ($case in $cases) {
    $source = Join-Path $Pages $case.file
    if (-not (Test-Path -LiteralPath $source)) { Write-Host "missing: $source"; continue }

    $harness = Join-Path $Pages ("harness-" + $case.file)
    New-Harness $source $harness
    $url = 'file:///' + ($harness -replace '\\', '/')

    Write-Host "=== $($case.name) ($($case.size)) ==="
    $dom = & $Chrome --headless --disable-gpu --no-sandbox --hide-scrollbars `
        --window-size=$($case.size) --virtual-time-budget=6000 --dump-dom $url 2>$null

    $match = [regex]::Match(($dom -join "`n"), '(?s)<pre id="lr-harness-out"[^>]*>(.*?)</pre>')
    if ($match.Success) {
        $json = [System.Net.WebUtility]::HtmlDecode($match.Groups[1].Value)
        try {
            $obj = $json | ConvertFrom-Json
            "start      : pages=$($obj.start.pages) page=$($obj.start.page) mode=$($obj.start.mode) stride=$($obj.start.stride)"
            "engine     : ready=$($obj.start.ready) shortChapter=$($obj.start.shortChapter) pullThreshold=$($obj.start.pullThreshold) hasNext=$($obj.start.hasNext)"
            "probe      : readyState=$($obj.probe.readyState) scrollTop=$($obj.probe.scrollTop)/$($obj.probe.scrollHeight) client=$($obj.probe.clientHeight) hint='$($obj.probe.hintText)'"
            "viewport   : $($obj.viewport.width) x $($obj.viewport.height)"
            "content    : scrollWidth=$($obj.scroll.scrollWidth) scrollHeight=$($obj.scroll.scrollHeight) col=$($obj.scroll.columnWidth)/$($obj.scroll.columnGap)"
            "margins    : left=$($obj.margins.left) right=$($obj.margins.right)"
            "bridge     : ready=$($obj.bridge.ready) locations=$($obj.bridge.locations) end=$($obj.bridge.end) fonts=$($obj.fonts)"
            "after walk : $($obj.final.page)/$($obj.final.pages) endCalls=$($obj.endAfterWalk)"
            "bottom end : before=$($obj.bottomBefore) after=$($obj.bottomAfter) triggered=$($obj.bottomReached)"
            "pull test  : hint='$($obj.pullHintOpacity)' transform='$($obj.pullTransform)' duringDrag=$($obj.pullDuringDrag) afterRelease=$($obj.pullAfterRelease)"
            foreach ($s in $obj.steps) {
                "  step page=$($s.page) offset=$($s.offsetBefore)->$($s.offsetAfter) $($s.transform)"
            }
            foreach ($e in $obj.errors) { "JS ERROR   : $e" }
            if ($obj.error) { "ERROR      : $($obj.error)" }
        } catch {
            "JSON parse failed: $($json.Substring(0, [Math]::Min(400, $json.Length)))"
        }
    } else {
        "no harness output captured"
    }

    $shot = Join-Path $OutDir ("$($case.name).png")
    & $Chrome --headless --disable-gpu --no-sandbox --hide-scrollbars `
        --window-size=$($case.size) --virtual-time-budget=4000 --screenshot=$shot $url 2>$null | Out-Null
    "screenshot : $shot"

    # Second pass: page one only, for a clean look at the layout.
    $clean = Join-Path $Pages ("clean-" + $case.file)
    $cleanHtml = Get-Content -LiteralPath $source -Raw -Encoding UTF8
    $cleanInject = '<pre id="lr-harness-out" style="display:none"></pre><script src="../harness-clean.js"></script>'
    $cleanHtml = $cleanHtml -replace '</body>', "$cleanInject`n</body>"
    $cleanHtml = $cleanHtml -replace '<head>', "<head>`n<script src=""../pre-harness.js""></script>"
    Set-Content -LiteralPath $clean -Value $cleanHtml -Encoding UTF8 -NoNewline
    $cleanUrl = 'file:///' + ($clean -replace '\\', '/')
    $cleanShot = Join-Path $OutDir ("$($case.name)-page1.png")
    & $Chrome --headless --disable-gpu --no-sandbox --hide-scrollbars `
        --window-size=$($case.size) --virtual-time-budget=4000 --screenshot=$cleanShot $cleanUrl 2>$null | Out-Null
    $cleanDom = & $Chrome --headless --disable-gpu --no-sandbox --hide-scrollbars `
        --window-size=$($case.size) --virtual-time-budget=6000 --dump-dom $cleanUrl 2>$null
    $cleanMatch = [regex]::Match(($cleanDom -join "`n"), '(?s)<pre id="lr-harness-out"[^>]*>(.*?)</pre>')
    if ($cleanMatch.Success) {
        $cleanJson = [System.Net.WebUtility]::HtmlDecode($cleanMatch.Groups[1].Value) | ConvertFrom-Json
        "page1 box  : firstTop=$($cleanJson.firstChildTop) firstLeft=$($cleanJson.firstChildLeft) paddingTop=$($cleanJson.paddingTop) marginTop=$($cleanJson.marginTop) mode=$($cleanJson.metrics.mode) pages=$($cleanJson.metrics.pages)"
        foreach ($e in $cleanJson.errors) { "JS ERROR   : $e" }
    }
    "page1 shot : $cleanShot"
    ""
}
