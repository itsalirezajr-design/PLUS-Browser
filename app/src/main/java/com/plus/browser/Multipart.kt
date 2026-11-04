package com.plus.browser

import java.util.Locale

internal data class MultipartPart(val groupName:String,val number:Int)
private val partPatterns=listOf(
    Regex("(?i)^(.*?)[._ -]?part[._ -]?(\\d+)(\\.[a-z0-9]{1,8})$"),
    Regex("(?i)^(.*?)\\.(\\d{3,})$"),
    Regex("(?i)^(.*?)[._ -](\\d+)(\\.[a-z0-9]{1,8})$")
)
internal fun parseMultipart(name:String):MultipartPart? {
    val m=partPatterns.firstNotNullOfOrNull {it.matchEntire(name)} ?: return null
    val number=m.groupValues[2].toIntOrNull() ?: return null
    val group=(m.groupValues[1]+m.groupValues.getOrElse(3){""}).lowercase(Locale.ROOT).trimEnd('.','_','-',' ')
    return MultipartPart(group,number)
}
