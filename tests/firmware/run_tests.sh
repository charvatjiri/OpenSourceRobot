#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

require_python() {
    if ! command -v python3 >/dev/null 2>&1; then
        echo "python3 was not found in PATH." >&2
        exit 1
    fi
}

have_python_modules() {
    python3 -c "import pytest, serial" >/dev/null 2>&1
}

install_ubuntu_deps() {
    local -a sudo_cmd=()

    if ! command -v apt-get >/dev/null 2>&1; then
        cat >&2 <<EOF
Automatic dependency installation is supported only on Ubuntu/Debian-style systems.

Install these packages manually, then rerun:
  python3
  python3-pytest
  python3-serial
EOF
        exit 1
    fi

    if command -v sudo >/dev/null 2>&1 && [ "${EUID}" -ne 0 ]; then
        sudo_cmd=(sudo)
    fi

    echo "Installing firmware test dependencies via apt-get..." >&2
    "${sudo_cmd[@]}" apt-get update
    "${sudo_cmd[@]}" apt-get install -y python3 python3-pytest python3-serial
}

main() {
    require_python
    cd "${ROOT_DIR}"

    if ! have_python_modules; then
        install_ubuntu_deps
    fi

    if ! have_python_modules; then
        echo "python3 still cannot import pytest and serial after installation." >&2
        exit 1
    fi

    exec python3 -m pytest \
        --rootdir="${ROOT_DIR}" \
        --capture=tee-sys \
        -o cache_dir="${ROOT_DIR}/.pytest_cache" \
        tests/firmware \
        "$@"
}

main "$@"
