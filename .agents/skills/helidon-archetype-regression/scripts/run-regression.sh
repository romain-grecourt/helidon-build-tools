#!/bin/bash
#
# Copyright (c) 2026 Oracle and/or its affiliates.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -uo pipefail

usage() {
    cat <<EOF
Usage:
  $(basename "$0") <diff_variations|diff_projects|compile_gate|all> [options]

Modes:
  diff_variations
      Ensure the variation coverage is unchanged.

  diff_projects
      Ensure generated project outputs are unchanged.

  compile_gate
      Ensure compiler complexity remains acceptable.

  all
      Run, in order, and stop on first failure:
      1. compile_gate
      2. diff_variations
      3. diff_projects

Options:
  --baseline-ref <rev>        Baseline git ref to install first (default: HEAD)
  --helidon-dir <path>        Required Helidon checkout location
  --state-dir <path>          Override the default .state root directory
  --threshold-seconds <n>     Compile gate threshold, default 5
  --verbose                   Echo commands as they run
  --no-restore-current        Skip reinstalling current workspace at end
  --help                      Show this usage text
EOF
}

die() {
    printf "ERROR: %s\n" "$*" >&2
    exit 2
}

step() {
    printf "%s\n" "$*"
}

trimmed_file() {
    sed '/^[[:space:]]*$/d' "$1"
}

yes_no() {
    if [ "$1" -eq 0 ]; then
        printf "no"
    elif [ "$1" -eq 1 ]; then
        printf "yes"
    else
        printf "unknown"
    fi
}

log_command() {
    if [ "${VERBOSE}" -eq 0 ]; then
        return 0
    fi
    printf "+"
    while [ "$#" -gt 0 ]; do
        printf " %q" "$1"
        shift
    done
    printf "\n"
}

run_logged() {
    local workdir log_file
    workdir="$1"
    log_file="$2"
    shift 2

    mkdir -p "$(dirname "$log_file")" || return 1
    log_command "$@"
    (
        cd "$workdir" || exit 1
        "$@" >"$log_file" 2>&1
    )
}

run_timed_logged() {
    local workdir log_file
    workdir="$1"
    log_file="$2"
    shift 2

    mkdir -p "$(dirname "$log_file")" || return 1
    log_command /usr/bin/time -p "$@"
    (
        cd "$workdir" || exit 1
        /usr/bin/time -p "$@" >"$log_file" 2>&1
    )
}

resolve_state_root() {
    if [[ "${STATE_ROOT}" = /* ]]; then
        return 0
    fi
    STATE_ROOT="${PWD}/${STATE_ROOT}"
}

validate_threshold() {
    if ! printf "%s\n" "${THRESHOLD_SECONDS}" | grep -Eq '^[0-9]+([.][0-9]+)?$'; then
        die "--threshold-seconds must be numeric"
    fi
}

validate_mode() {
    case "${MODE}" in
    diff_variations|diff_projects|compile_gate|all)
        ;;
    *)
        die "unknown mode '${MODE}'"
        ;;
    esac
}

validate_paths() {
    local baseline_sha

    [ -n "${HELIDON_DIR}" ] || die "--helidon-dir is required"
    [ -d "${HELIDON_DIR}" ] || die "Helidon checkout not found: ${HELIDON_DIR}"
    HELIDON_DIR="$(cd "${HELIDON_DIR}" && pwd)" || die "cannot resolve --helidon-dir"

    HELIDON_POM="${HELIDON_DIR}/archetypes/archetypes/pom.xml"
    HELPER_SCRIPT="${HELIDON_DIR}/archetypes/archetypes/etc/projects-diff.sh"
    HELIDON_TARGET_DIR="${HELIDON_DIR}/archetypes/archetypes/target/tests"

    [ -f "${HELIDON_POM}" ] || die "missing ${HELIDON_POM}"
    [ -x "${HELPER_SCRIPT}" ] || die "missing executable helper ${HELPER_SCRIPT}"

    validate_threshold
    resolve_state_root

    if [ "${MODE}" = "diff_variations" ] || [ "${MODE}" = "diff_projects" ] || [ "${MODE}" = "all" ]; then
        baseline_sha="$(git -C "${WORKSPACE_DIR}" rev-parse --verify \
            "${BASELINE_REF}^{commit}" 2>/dev/null)" \
            || die "invalid --baseline-ref '${BASELINE_REF}'"
        BASELINE_SHA="${baseline_sha}"
    fi
}

cleanup_state_dir() {
    local state_dir worktree_dir
    state_dir="$1"
    for worktree_dir in \
        "${state_dir}/worktrees/baseline" \
        "${state_dir}/baseline"; do
        if [ -d "${worktree_dir}" ]; then
            git -C "${WORKSPACE_DIR}" worktree remove --force \
                "${worktree_dir}" >/dev/null 2>&1 || true
        fi
    done

    rm -rf "${state_dir}" || return 1
}

cleanup_legacy_state_layout() {
    local legacy_dir
    for legacy_dir in logs snapshots worktrees; do
        if [ -e "${STATE_ROOT}/${legacy_dir}" ]; then
            step "state: removing legacy ${STATE_ROOT}/${legacy_dir}"
            cleanup_state_dir "${STATE_ROOT}/${legacy_dir}" || return 1
        fi
    done

    return 0
}

prune_state_dirs() {
    local dirs remaining dir
    dirs=()
    while IFS= read -r dir; do
        dirs+=("${dir}")
    done < <(
        find "${STATE_ROOT}" -mindepth 1 -maxdepth 1 -type d -print | LC_ALL=C sort
    )
    remaining="${#dirs[@]}"

    if [ "${remaining}" -le "${STATE_DIR_LIMIT}" ]; then
        return 0
    fi

    for dir in "${dirs[@]}"; do
        if [ "${remaining}" -le "${STATE_DIR_LIMIT}" ]; then
            break
        fi
        if [ "${dir}" = "${STATE_DIR}" ]; then
            continue
        fi

        step "state: pruning ${dir}"
        cleanup_state_dir "${dir}" || return 1
        remaining=$((remaining - 1))
    done

    return 0
}

create_state_dir() {
    RUN_ID="$(date +%Y%m%d-%H%M%S)-$$"
    mkdir -p "${STATE_ROOT}" || return 1
    cleanup_legacy_state_layout || return 1

    STATE_DIR="${STATE_ROOT}/${RUN_ID}"
    mkdir "${STATE_DIR}" || return 1
    mkdir -p "${STATE_DIR}/logs" \
        "${STATE_DIR}/worktrees" \
        "${STATE_DIR}/snapshots" || return 1

    RESTORE_LOG="${STATE_DIR}/logs/restore-current.log"
    BASELINE_WORKTREE_DIR="${STATE_DIR}/worktrees/baseline"
    BASELINE_WORKTREE_LOG="${STATE_DIR}/logs/baseline-worktree.log"

    prune_state_dirs || return 1
    return 0
}

ensure_baseline_worktree() {
    local current_sha

    if [ -d "${BASELINE_WORKTREE_DIR}" ]; then
        current_sha="$(git -C "${BASELINE_WORKTREE_DIR}" rev-parse HEAD \
            2>/dev/null || true)"
        if [ -n "${current_sha}" ] && [ "${current_sha}" = "${BASELINE_SHA}" ]; then
            step "state: reusing baseline worktree ${BASELINE_WORKTREE_DIR}"
            return 0
        fi

        git -C "${WORKSPACE_DIR}" worktree remove --force \
            "${BASELINE_WORKTREE_DIR}" >/dev/null 2>&1 || true
        rm -rf "${BASELINE_WORKTREE_DIR}" || return 1
    fi

    step "state: preparing baseline worktree at ${BASELINE_REF}"
    run_logged "${WORKSPACE_DIR}" "${BASELINE_WORKTREE_LOG}" \
        git worktree add --detach "${BASELINE_WORKTREE_DIR}" "${BASELINE_REF}"
}

install_build_tools() {
    local source_dir log_file label
    source_dir="$1"
    log_file="$2"
    label="$3"

    step "${CURRENT_MODE}: installing ${label} build-tools"
    run_logged "${source_dir}" "${log_file}" \
        mvn -pl maven-plugins/helidon-archetype-maven-plugin \
            -am \
            install \
            -DskipTests
}

install_baseline() {
    CURRENT_WORKSPACE_INSTALLED=0
    install_build_tools "$1" "$2" "$3"
}

install_current() {
    if install_build_tools "${WORKSPACE_DIR}" "$1" "$2"; then
        CURRENT_WORKSPACE_INSTALLED=1
        return 0
    fi
    CURRENT_WORKSPACE_INSTALLED=0
    return 1
}

generate_variations() {
    local log_file
    log_file="$1"

    step "${CURRENT_MODE}: generating variation snapshot"
    run_logged "${HELIDON_DIR}" "${log_file}" \
        mvn -f archetypes/archetypes/pom.xml \
            clean \
            install \
            -Darchetype.test.variationsOnly=true
}

generate_projects() {
    local log_file
    log_file="$1"

    step "${CURRENT_MODE}: generating project snapshot"
    run_logged "${HELIDON_DIR}" "${log_file}" \
        mvn -f archetypes/archetypes/pom.xml \
            clean \
            install \
            -Darchetype.test.generateOnly=true \
            -Darchetype.test.parallelGeneration=true
}

copy_variations_snapshot() {
    local dest_dir
    dest_dir="$1"

    mkdir -p "${dest_dir}" || return 1
    [ -f "${HELIDON_TARGET_DIR}/projects.csv" ] \
        || { printf "ERROR: %s/projects.csv not found\n" "${HELIDON_TARGET_DIR}" >&2; return 1; }
    cp "${HELIDON_TARGET_DIR}/projects.csv" "${dest_dir}/projects.csv" || return 1
}

copy_projects_snapshot() {
    local dest_dir
    dest_dir="$1"

    mkdir -p "${dest_dir}" || return 1
    [ -d "${HELIDON_TARGET_DIR}" ] \
        || { printf "ERROR: %s not found\n" "${HELIDON_TARGET_DIR}" >&2; return 1; }
    cp -R "${HELIDON_TARGET_DIR}/." "${dest_dir}/" || return 1
}

run_helper_compare() {
    local log_file command orig_dir actual_dir
    log_file="$1"
    command="$2"
    orig_dir="$3"
    actual_dir="$4"

    run_logged "${WORKSPACE_DIR}" "${log_file}" \
        "${HELPER_SCRIPT}" \
        "--orig=${orig_dir}" \
        "--actual=${actual_dir}" \
        "${command}"
}

log_has_text() {
    grep -q '[^[:space:]]' "$1"
}

compile_gate_passes_threshold() {
    local measured
    measured="$1"

    awk -v measured="${measured}" \
        -v threshold="${THRESHOLD_SECONDS}" \
        'BEGIN { exit !(measured <= threshold) }'
}

print_compile_gate_summary() {
    local status wall_seconds timing_log
    status="$1"
    wall_seconds="$2"
    timing_log="$3"

    printf "\n"
    printf "compile_gate: %s\n" "${status}"
    printf "  measured wall-clock: %ss\n" "${wall_seconds}"
    printf "  threshold: %ss\n" "${THRESHOLD_SECONDS}"
    printf "  timing log: %s\n" "${timing_log}"
}

print_diff_variations_summary() {
    local status changed snapshot_root compare_log
    status="$1"
    changed="$2"
    snapshot_root="$3"
    compare_log="$4"

    printf "\n"
    printf "diff_variations: %s\n" "${status}"
    printf "  outputs changed: %s\n" "$(yes_no "${changed}")"
    printf "  snapshots: %s\n" "${snapshot_root}"
    printf "  compare log: %s\n" "${compare_log}"
}

print_diff_projects_summary() {
    local status changed csv_changed tree_changed snapshot_root compare_log
    status="$1"
    changed="$2"
    csv_changed="$3"
    tree_changed="$4"
    snapshot_root="$5"
    compare_log="$6"

    printf "\n"
    printf "diff_projects: %s\n" "${status}"
    printf "  outputs changed: %s\n" "$(yes_no "${changed}")"
    printf "  csv changed: %s\n" "$(yes_no "${csv_changed}")"
    printf "  project trees changed: %s\n" "$(yes_no "${tree_changed}")"
    printf "  snapshots: %s\n" "${snapshot_root}"
    printf "  compare log: %s\n" "${compare_log}"
}

print_restore_note() {
    local note
    note="$1"

    printf "\n%s\n" "${note}"
}

run_compile_gate() {
    local install_log status timing_log timing_value wall_seconds
    CURRENT_MODE="compile_gate"
    status="FAIL"
    wall_seconds="n/a"

    install_log="${STATE_DIR}/logs/compile_gate-install.log"
    timing_log="${STATE_DIR}/logs/compile_gate.log"

    if ! install_current "${install_log}" "current workspace"; then
        print_compile_gate_summary "${status}" "${wall_seconds}" "${timing_log}"
        return 1
    fi

    step "${CURRENT_MODE}: timing Helidon archetype compile"
    if ! run_timed_logged "${HELIDON_DIR}" "${timing_log}" \
        mvn -f archetypes/archetypes/pom.xml \
            compile \
            -e \
            -Dhelidon.build.archetype.engine.v2.debugReduction=true; then
        print_compile_gate_summary "${status}" "${wall_seconds}" "${timing_log}"
        return 1
    fi

    timing_value="$(awk '/^real /{value=$2} END{print value}' "${timing_log}")"
    if [ -z "${timing_value}" ]; then
        wall_seconds="missing"
        print_compile_gate_summary "${status}" "${wall_seconds}" "${timing_log}"
        return 1
    fi

    wall_seconds="${timing_value}"
    if compile_gate_passes_threshold "${wall_seconds}"; then
        status="PASS"
        print_compile_gate_summary "${status}" "${wall_seconds}" "${timing_log}"
        return 0
    fi

    print_compile_gate_summary "${status}" "${wall_seconds}" "${timing_log}"
    return 1
}

run_diff_variations() {
    local baseline_install_log baseline_generate_log
    local actual_install_log actual_generate_log compare_log
    local baseline_snapshot actual_snapshot snapshot_root status changed

    CURRENT_MODE="diff_variations"
    status="FAIL"
    changed=2

    baseline_install_log="${STATE_DIR}/logs/diff_variations-baseline-install.log"
    baseline_generate_log="${STATE_DIR}/logs/diff_variations-baseline-generate.log"
    actual_install_log="${STATE_DIR}/logs/diff_variations-actual-install.log"
    actual_generate_log="${STATE_DIR}/logs/diff_variations-actual-generate.log"
    compare_log="${STATE_DIR}/logs/diff_variations-compare.log"
    snapshot_root="${STATE_DIR}/snapshots/diff_variations"
    baseline_snapshot="${snapshot_root}/baseline"
    actual_snapshot="${snapshot_root}/actual"

    if ! install_baseline "${BASELINE_WORKTREE_DIR}" "${baseline_install_log}" "baseline (${BASELINE_REF})" \
        || ! generate_variations "${baseline_generate_log}" || ! copy_variations_snapshot "${baseline_snapshot}" \
        || ! install_current "${actual_install_log}" "current workspace" || ! generate_variations "${actual_generate_log}" \
        || ! copy_variations_snapshot "${actual_snapshot}" \
        || ! run_helper_compare "${compare_log}" diff_csv "${baseline_snapshot}" "${actual_snapshot}"; then
        print_diff_variations_summary "${status}" "${changed}" "${snapshot_root}" "${compare_log}"
        return 1
    fi

    if log_has_text "${compare_log}"; then
        changed=1
    else
        changed=0
    fi

    if [ "${changed}" -eq 0 ]; then
        status="PASS"
        print_diff_variations_summary "${status}" "${changed}" "${snapshot_root}" "${compare_log}"
        return 0
    fi

    print_diff_variations_summary "${status}" "${changed}" "${snapshot_root}" "${compare_log}"
    return 1
}

run_diff_projects() {
    local baseline_install_log baseline_generate_log
    local actual_install_log actual_generate_log csv_log compare_log
    local actual_snapshot baseline_snapshot changed csv_changed
    local helper_text snapshot_root status tree_changed

    CURRENT_MODE="diff_projects"
    status="FAIL"
    changed=2
    csv_changed=2
    tree_changed=2

    baseline_install_log="${STATE_DIR}/logs/diff_projects-baseline-install.log"
    baseline_generate_log="${STATE_DIR}/logs/diff_projects-baseline-generate.log"
    actual_install_log="${STATE_DIR}/logs/diff_projects-actual-install.log"
    actual_generate_log="${STATE_DIR}/logs/diff_projects-actual-generate.log"
    csv_log="${STATE_DIR}/logs/diff_projects-diff_csv.log"
    compare_log="${STATE_DIR}/logs/diff_projects-compare.log"
    snapshot_root="${STATE_DIR}/snapshots/diff_projects"
    baseline_snapshot="${snapshot_root}/baseline"
    actual_snapshot="${snapshot_root}/actual"

    if ! install_baseline "${BASELINE_WORKTREE_DIR}" "${baseline_install_log}" "baseline (${BASELINE_REF})" \
        || ! generate_projects "${baseline_generate_log}" || ! copy_projects_snapshot "${baseline_snapshot}" \
        || ! install_current "${actual_install_log}" "current workspace" || ! generate_projects "${actual_generate_log}" \
        || ! copy_projects_snapshot "${actual_snapshot}" \
        || ! run_helper_compare "${csv_log}" diff_csv "${baseline_snapshot}" "${actual_snapshot}"; then
        print_diff_projects_summary "${status}" "${changed}" "${csv_changed}" "${tree_changed}" \
            "${snapshot_root}" "${compare_log}"
        return 1
    fi
    if log_has_text "${csv_log}"; then
        csv_changed=1
    else
        csv_changed=0
    fi

    if ! run_helper_compare "${compare_log}" diff_projects "${baseline_snapshot}" "${actual_snapshot}"; then
        print_diff_projects_summary "${status}" "${changed}" "${csv_changed}" "${tree_changed}" \
            "${snapshot_root}" "${compare_log}"
        return 1
    fi

    helper_text="$(trimmed_file "${compare_log}")"
    if [ -n "${helper_text}" ] && [ "${helper_text}" != "OK" ]; then
        tree_changed=1
    else
        tree_changed=0
    fi

    if [ "${csv_changed}" -eq 1 ] || [ "${tree_changed}" -eq 1 ]; then
        changed=1
    else
        changed=0
    fi

    if [ "${changed}" -eq 0 ]; then
        status="PASS"
        print_diff_projects_summary "${status}" "${changed}" "${csv_changed}" "${tree_changed}" \
            "${snapshot_root}" "${compare_log}"
        return 0
    fi

    print_diff_projects_summary "${status}" "${changed}" "${csv_changed}" "${tree_changed}" \
        "${snapshot_root}" "${compare_log}"
    return 1
}

run_all() {
    if ! run_compile_gate; then
        printf "\n"
        printf "all: FAIL (stopped at %s)\n" "compile_gate"
        return 1
    fi
    if ! run_diff_variations; then
        printf "\n"
        printf "all: FAIL (stopped at %s)\n" "diff_variations"
        return 1
    fi
    if ! run_diff_projects; then
        printf "\n"
        printf "all: FAIL (stopped at %s)\n" "diff_projects"
        return 1
    fi
    printf "\n"
    printf "all: PASS\n"
    return 0
}

restore_current_if_needed() {
    if [ "${RESTORE_CURRENT}" -eq 0 ]; then
        RESTORE_NOTE="${LOCAL_M2_REPO} restore skipped (--no-restore-current)."
        return 0
    fi

    if [ "${CURRENT_WORKSPACE_INSTALLED}" -eq 1 ]; then
        RESTORE_NOTE="${LOCAL_M2_REPO} contains the current workspace install."
        return 0
    fi

    CURRENT_MODE="restore"
    step "restore: reinstalling current workspace into ${LOCAL_M2_REPO}"
    if ! install_current "${RESTORE_LOG}" "current workspace"; then
        RESTORE_NOTE="${LOCAL_M2_REPO} restore failed. See ${RESTORE_LOG}."
        return 1
    fi
    RESTORE_NOTE="${LOCAL_M2_REPO} restored to the current workspace install."
    return 0
}

parse_args() {
    if [ "$#" -eq 0 ]; then
        usage >&2
        exit 2
    fi

    if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
        usage
        exit 0
    fi

    MODE="$1"
    shift

    while [ "$#" -gt 0 ]; do
        case "$1" in
        --baseline-ref)
            [ "$#" -ge 2 ] || die "missing value for --baseline-ref"
            BASELINE_REF="$2"
            shift 2
            ;;
        --helidon-dir)
            [ "$#" -ge 2 ] || die "missing value for --helidon-dir"
            HELIDON_DIR="$2"
            shift 2
            ;;
        --state-dir)
            [ "$#" -ge 2 ] || die "missing value for --state-dir"
            STATE_ROOT="$2"
            shift 2
            ;;
        --threshold-seconds)
            [ "$#" -ge 2 ] || die "missing value for --threshold-seconds"
            THRESHOLD_SECONDS="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=1
            shift
            ;;
        --no-restore-current)
            RESTORE_CURRENT=0
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            die "unknown option '${1}'"
            ;;
        esac
    done
}

main() {
    local status restore_status

    parse_args "$@"
    validate_mode
    validate_paths
    create_state_dir || exit 1
    step "state: ${STATE_DIR}"

    case "${MODE}" in
    diff_variations|diff_projects|all)
        ensure_baseline_worktree || exit 1
        ;;
    esac

    status=0
    case "${MODE}" in
    compile_gate)
        run_compile_gate || status=$?
        ;;
    diff_variations)
        run_diff_variations || status=$?
        ;;
    diff_projects)
        run_diff_projects || status=$?
        ;;
    all)
        run_all || status=$?
        ;;
    esac

    restore_status=0
    restore_current_if_needed || restore_status=$?
    print_restore_note "${RESTORE_NOTE}"

    if [ "${status}" -eq 0 ] && [ "${restore_status}" -ne 0 ]; then
        exit "${restore_status}"
    fi
    exit "${status}"
}

MODE=""
BASELINE_REF="HEAD"
HELIDON_DIR=""
STATE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.state"
STATE_DIR=""
STATE_DIR_LIMIT=10
THRESHOLD_SECONDS="5"
VERBOSE=0
RESTORE_CURRENT=1
RUN_ID=""
CURRENT_MODE=""
CURRENT_WORKSPACE_INSTALLED=0
RESTORE_NOTE=""
RESTORE_LOG=""
BASELINE_WORKTREE_DIR=""
BASELINE_WORKTREE_LOG=""
BASELINE_SHA=""
HELIDON_POM=""
HELPER_SCRIPT=""
HELIDON_TARGET_DIR=""
LOCAL_M2_REPO="${HOME}/.m2"
WORKSPACE_DIR="$(git -C "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" \
    rev-parse --show-toplevel 2>/dev/null)" \
    || die "run-regression.sh must live inside a git checkout"

main "$@"
