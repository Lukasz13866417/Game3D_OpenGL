#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
trace_dir="$project_root/build/traces"
simulator="$project_root/simulator/build/install/simulator/bin/simulator"
visualizer="$project_root/tools/visualize_simulation.py"

mkdir -p "$trace_dir"
cd "$project_root"
./gradlew -q :simulator:installDist --no-daemon

scenarios=(
  flat_rest
  ground_jump
  jump_charge_x_boundary_accept
  jump_charge_x_ratio_reject
  jump_charge_x_absolute_reject
  slope_boost
  gap_recovery
  spike_avoidance
  down_hold_no_bounce
  down_hold_then_charge
  down_release_bounces
  landing_buffer_near_safe
  landing_buffer_too_early
  landing_buffer_rising
  landing_buffer_rising_bounce
  landing_buffer_rising_ramp
  feather_collection
  airborne_redirect
  open_lift
  streaming_commit
  generated_gameplay_stream
)

for scenario in "${scenarios[@]}"; do
  trace_level="summary"
  if [[ "$scenario" == "ground_jump" ]]; then
    trace_level="contacts"
  fi
  trace="$trace_dir/final-$scenario.ndjson"
  svg="$trace_dir/final-$scenario.svg"
  "$simulator" run "$scenario" --trace "$trace_level" --out "$trace"
  python3 "$visualizer" "$trace" --output "$svg" --focus-traveled --samples
done

python3 "$visualizer" "$trace_dir/final-ground_jump.ndjson" \
  --output "$trace_dir/final-ground_jump-debug.svg" \
  --focus-traveled --samples --solver-debug
python3 "$visualizer" "$trace_dir/final-ground_jump.ndjson" \
  --output "$trace_dir/final-ground-jump.svg" --focus-traveled --samples
python3 "$visualizer" "$trace_dir/final-ground_jump.ndjson" \
  --output "$trace_dir/final-ground-jump-debug.svg" \
  --focus-traveled --samples --solver-debug
python3 "$visualizer" "$trace_dir/final-ground_jump.ndjson" \
  --output "$trace_dir/ground-jump-visual.svg" --focus-traveled --samples
python3 "$visualizer" "$trace_dir/final-airborne_redirect.ndjson" \
  --output "$trace_dir/final-airborne_redirect-lateral.svg" \
  --horizontal z --vertical x --focus-traveled --samples
