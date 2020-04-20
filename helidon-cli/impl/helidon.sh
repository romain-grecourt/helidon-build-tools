#!/bin/bash

#
# Copyright (c) 2020, 2020 Oracle and/or its affiliates.
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

main() {
    local action command
    init "$@"
    ${action} ${command}
}

init() {
    local -r projectDir=$(dirname "${0}")
    local -r targetDir="${projectDir}/target/"
    local -r jarFile="${targetDir}/helidon.jar"
    local -r debug="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
    local jvm
    local args
    action=exec

    while (( ${#} > 0 )); do
        case "${1}" in
            --debug) appendVar jvm "${debug}" ;;
            --dryRun) action=echo ;;
            *) appendVar args "${1}" ;;
        esac
        shift
    done

    command="java ${jvm} -jar ${jarFile} ${args}"
}

appendVar() {
    export ${1}="${!1:+${!1} }${2}"
}

main "$@"

