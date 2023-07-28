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

shopt -s globstar
shopt -s extglob

#
# Expand the given glob expressions to match directories with pom.xml files.
# Exclude directories that are nested under 'src'
#
# args: prefix expr...
#
list_modules() {
  local prefix files
  prefix="${1}"
  shift
  files=()
  for exp in "${@}" ; do
    for i in ${exp}/pom.xml ; do
      if [[ ! "${i}" =~ "src/" ]] ; then
        files+=("${prefix}${i%%/pom.xml}")
      fi
    done
  done
  IFS=","
  echo "${files[*]}"
}

#
# Print a JSON object for a group
#
# args: group prefix expr...
#
print_group() {
  local group
  group="${1}"
  shift
  echo -ne '
  {
      "group": "'"${group}"'",
      "modules": "'"$(list_modules "${@}")"'"
  }'
}

#
# Print comma separated JSON object for the groups.
# Always add a 'misc' at the end that matches everything else
#
# arg1: JSON object E.g. '{ "group1": [ "dir1/**", "dir2/**" ], "group2": [ "dir3/**" ] }'
#
print_groups() {
  local modules all_modules
  all_modules=()
  for group in $(jq -r 'keys | .[]' <<< "${1}") ; do
    readarray -t modules <<< "$(jq -r --arg a "${group}" '. | to_entries[] | select (.key == $a).value[]' <<< "${1}")"
    print_group "${group}" "" "${modules[@]}"
    echo -ne ","
    all_modules+=("${modules[@]}")
  done
  if [ ${#all_modules[@]} -gt 0 ] ; then
      print_group "misc" "!" "${all_modules[@]}"
  fi
}

#
# Generate the 'matrix' output
#
# arg1: JSON object E.g. '{ "group1": [ "dir1/**", "dir2/**" ], "group2": [ "dir3/**" ] }'
#
matrix() {
 echo "matrix=$(echo '{
   "include": [
      '"$(print_groups "${1}")"'
    ]
 }' | jq -c)"
}

matrix "${1}"
