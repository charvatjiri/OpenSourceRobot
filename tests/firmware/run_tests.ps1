$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Get-PythonCommand {
    if (Get-Command py -ErrorAction SilentlyContinue) {
        return @("py", "-3")
    }

    if (Get-Command python -ErrorAction SilentlyContinue) {
        return @("python")
    }

    throw "Neither 'py' nor 'python' was found in PATH."
}

function Test-PythonModules {
    param (
        [string[]]$PythonCmd
    )

    $checkArgs = $PythonCmd + @("-c", "import pytest, serial")
    & $checkArgs[0] $checkArgs[1..($checkArgs.Length - 1)] *> $null
    return $LASTEXITCODE -eq 0
}

function Install-WindowsDeps {
    param (
        [string[]]$PythonCmd
    )

    Write-Host "Installing firmware test dependencies with pip --user..."
    $installArgs = $PythonCmd + @(
        "-m",
        "pip",
        "install",
        "--user",
        "pytest>=8.0,<9.0",
        "pyserial>=3.5,<4.0"
    )
    & $installArgs[0] $installArgs[1..($installArgs.Length - 1)]
}

$pythonCmd = Get-PythonCommand

if (-not (Test-PythonModules -PythonCmd $pythonCmd)) {
    Install-WindowsDeps -PythonCmd $pythonCmd
}

if (-not (Test-PythonModules -PythonCmd $pythonCmd)) {
    throw "Python still cannot import pytest and serial after installation."
}

$pytestArgs = $pythonCmd + @(
    "-m",
    "pytest",
    "--rootdir=$RootDir",
    "--capture=tee-sys",
    "-o",
    "cache_dir=$(Join-Path $RootDir '.pytest_cache')",
    (Join-Path $RootDir "tests/firmware")
) + $args
& $pytestArgs[0] $pytestArgs[1..($pytestArgs.Length - 1)]
exit $LASTEXITCODE
