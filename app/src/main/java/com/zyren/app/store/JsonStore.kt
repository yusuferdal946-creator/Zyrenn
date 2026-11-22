package com.zyren.app.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import java.io.File

/** Basit, atomik JSON dizisi deposu (uygulamaya ayrılmış .zyren/ klasöründe). */
class JsonStore(private val file: File) {
    @Synchronized
    fun read(): JsonArray = runCatching { Json.parseToJsonElement(file.readText()).jsonArray }.getOrDefault(JsonArray(emptyList()))

    @Synchronized
    fun write(a: JsonArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(a.toString())
        if (!tmp.renameTo(file)) { file.writeText(a.toString()); tmp.delete() }
    }
}
