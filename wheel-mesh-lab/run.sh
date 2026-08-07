#!/usr/bin/env bash
set -euo pipefail

LAB_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

cmake -S "$LAB_DIR" -B "$LAB_DIR/build" -DCMAKE_BUILD_TYPE=Debug
cmake --build "$LAB_DIR/build" --parallel
exec "$LAB_DIR/build/wheel_mesh_lab" "$@"
