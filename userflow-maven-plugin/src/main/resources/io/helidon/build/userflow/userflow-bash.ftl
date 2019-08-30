<#ftl strip_text=true>

<#-- conditional expression -->
<#macro not arg>! { <@expression e=arg /> ;}</#macro>
<#macro is_not arg1 arg2>! { <@is arg1=arg1 arg2=arg2 /> ;}</#macro>
<#macro and arg1 arg2>{ <@expression e=arg1 /> && <@expression e=arg2 /> ;}</#macro>
<#macro is arg1 arg2>{ <@and arg1=arg1 arg2=arg2 /> || { <@not arg=arg1 /> && <@not arg=arg2 /> ;} ;}</#macro>
<#macro or arg1 arg2>{ <@expression e=arg1 /> || <@expression e=arg2 /> ;}</#macro>
<#macro xor arg1 arg2>{ <@or arg1=arg1 arg2=arg2 /> && ! { <@and arg1=arg1 arg2=arg2 /> ;} ;}</#macro>

<#!-- value expression -->
<#macro not_equal arg1 arg2>[ <@value v=arg1 /> != <@value v=arg2 /> ]</#macro>
<#macro equal arg1 arg2>[ <@value v=arg1 /> = <@value v=arg2 /> ]</#macro>

<#macro value v><#if v.isVariable>"$${v.value}"<#else>"${v.value}"</#if></#macro>
<#macro expression e>
    <#switch e.type>
      <#case "IS">
        <@is arg1=e.left arg2=e.right />
        <#break>
      <#case "IS_NOT">
        <@is_not arg1=e.left arg2=e.right />
        <#break>
      <#case "AND">
        <@and arg1=e.left arg2=e.right />
        <#break>
      <#case "OR">
        <@or arg1=e.left arg2=e.right />
        <#break>
      <#case "XOR">
        <@xor arg1=e.left arg2=e.right />
        <#break>
      <#case "NOT">
        <@not arg=e.right />
        <#break>
      <#case "NOT_EQUAL">
        <@not_equal arg1=e.left arg2=e.right />
        <#break>
      <#case "EQUAL">
        <@equal arg1=e.left arg2=e.right />
        <#break>
      <#default>
    </#switch>
</#macro>

<#!-- generate the step functions -->
<#list steps as step>
<#if step.predicate??>
function do_${step.name}() { if <@expression step.predicate /> ;then echo true ; else echo false ;fi ;}
</#if>
function validate_${step.name}() { if <@expression step.validation /> ;then echo true ; else echo false ;fi ;}
</#list>