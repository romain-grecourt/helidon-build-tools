#!/usr/bin/env bash -e

# functions
${bashIncludes}

# variables
<#list flow.steps as step>
${step.name}='${step.default?if_exists}'
</#list>

printf "\n--> WHAT TO EAT? <--\n\n"

# flow
<#macro predicate s><#if s.predicate??>if `do_${s.name}` ; then <#nested> ; fi<#else><#nested></#if></#macro>
<#macro default s><#if s.default??> ; printf '(default: ${s.default}) '</#if></#macro>
<#macro read s>read ${s.name}<#if s.default??> ; ${s.name}=${r"${"}${s.name}:-${s.default}}</#if></#macro>
<#macro prompt s>printf '${s.text}: '<@default s=s/> ; <@read s=s /></#macro>
<#macro validate s>while ! `validate_${s.name}` ; do printf '${s.error}: ' ; <@read s=s /> ; done</#macro>
<#list flow.steps as step>
<@predicate s=step><@prompt s=step/> ; <@validate s=step /></@predicate>
</#list>

# results
printf "\n--> RESULTS <--\n\n"
<#list flow.steps as step>
printf "${step.name}=%s\n" "$${step.name}"
</#list>
printf "\n"