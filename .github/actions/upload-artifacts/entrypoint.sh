#!/bin/bash
#
# Copyright (c) 2023 Oracle and/or its affiliates.
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

set -o pipefail || true  # trace ERR through pipes
set -o errtrace || true # trace ERR through commands and functions
set -o errexit || true  # exit the script if any statement returns a non-true return value

on_error(){
  CODE="${?}" && \
  set +x && \
  printf "[ERROR] Error(code=%s) occurred at %s:%s command: %s\n" \
      "${CODE}" "${BASH_SOURCE[0]}" "${LINENO}" "${BASH_COMMAND}"
}
trap on_error ERR

shopt -s globstar # recursive glob using '**'
shopt -s dotglob # glob hidden files and directories

input() {
  env | (grep -E "^INPUT_${1^^}=" 2> /dev/null || true) | cut -d '=' -f2
}

IF_NO_FILES_FOUND=$(input 'if-no-files-found')
FILES=( )

# handle 'path'
for path_exp in ${INPUT_PATH}; do
  for file in $(realpath --relative-to "${PWD}" "${path_exp}" 2> /dev/null || true) ; do
    FILES+=("${file}")
  done
done

# handle 'if-no-files-found'
if [ ${#FILES[*]} -eq 0 ]; then
  if [ "${IF_NO_FILES_FOUND}" = "warn" ]; then
    echo "WARNING: no files found for paths: ${INPUT_PATH}"
    exit 0
  elif [ "${IF_NO_FILES_FOUND}" = "error" ]; then
    exit 1
  else
    exit 0
  fi
fi

printf "\nResolved files:\n"
# shellcheck disable=SC2068
printf -- "- %s\n" ${FILES[@]}
printf "\n"

HEADERS=(
  --header 'Accept: application/json;api-version=6.0-preview'
  --header "Authorization: Bearer ${ACTIONS_RUNTIME_TOKEN}"
)
ARTIFACT_BASE="${ACTIONS_RUNTIME_URL}_apis/pipelines/workflows/${GITHUB_RUN_ID}/artifacts?api-version=6.0-preview"

RESOURCE_URL="$(
  curl \
    -XPOST \
    --silent \
    --fail-with-body \
    "${HEADERS[@]}" \
    --header 'Content-Type: application/json' \
    --data '{"type": "actions_storage", "name": "'"${INPUT_NAME}"'"}' \
    "${ARTIFACT_BASE}" | jq --exit-status --raw-output .fileContainerResourceUrl
)"

TOTAL_SIZE=0
# shellcheck disable=SC2068
for file in ${FILES[@]} ; do
  echo "Uploading ${file}"
  FILE_SIZE=$(stat -c '%s' "${file}")
  TOTAL_SIZE=$((TOTAL_SIZE+FILE_SIZE))
  curl \
    -XPUT \
    --silent \
    --fail-with-body \
    -o /dev/null \
    "${HEADERS[@]}" \
    --header 'Content-Type: application/octet-stream' \
    --header "Content-Range: bytes 0-$((FILE_SIZE-1))/${FILE_SIZE}" \
    -T "${file}" \
    "${RESOURCE_URL}?itemPath=${INPUT_NAME}/$(jq -rn --arg x "${file}" '$x|@uri')"
done

echo "Finalizing artifact..."
curl \
  -XPATCH \
  --silent \
  --fail-with-body \
  -o /dev/null \
  "${HEADERS[@]}" \
  --header 'Content-Type: application/json' \
  --data '{"size": '"${TOTAL_SIZE}"'}' \
  "${ARTIFACT_BASE}&artifactName=${INPUT_NAME}"
