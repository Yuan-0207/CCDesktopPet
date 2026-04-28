param(
    [string]$BlenderExe = "D:\Program Files\Blender Foundation\Blender 5.1\blender.exe",
    [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BlenderExe)) {
    throw "Blender not found: $BlenderExe"
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Split-Path -Path $PSScriptRoot -Parent
}

$RenderScript = Join-Path $PSScriptRoot "render_glb_thumbnail.py"
if (-not (Test-Path $RenderScript)) {
    throw "Render script not found: $RenderScript"
}

$targets = @(
    @{
        model = Join-Path $ProjectRoot "app\src\main\assets\models\DefaultModel.glb"
        out = Join-Path $ProjectRoot "app\src\main\res\drawable\preview_default_model.png"
    },
    @{
        model = Join-Path $ProjectRoot "app\src\main\assets\models\BabyDragon.glb"
        out = Join-Path $ProjectRoot "app\src\main\res\drawable\preview_baby_dragon.png"
    }
)

foreach ($t in $targets) {
    if (-not (Test-Path $t.model)) {
        throw "Model not found: $($t.model)"
    }
    Write-Host "Rendering thumbnail from $($t.model)"
    & $BlenderExe --background --python $RenderScript -- --model $t.model --output $t.out --size 512
    if ($LASTEXITCODE -ne 0) {
        throw "Blender render failed for: $($t.model)"
    }
    if (-not (Test-Path $t.out)) {
        throw "Output PNG missing: $($t.out)"
    }
}

Write-Host ""
Write-Host "Done. Generated thumbnails:"
Get-Item (Join-Path $ProjectRoot "app\src\main\res\drawable\preview_default_model.png"),
         (Join-Path $ProjectRoot "app\src\main\res\drawable\preview_baby_dragon.png") |
    Select-Object Name, Length | Format-Table -AutoSize

