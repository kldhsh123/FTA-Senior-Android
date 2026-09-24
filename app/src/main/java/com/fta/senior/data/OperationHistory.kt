package com.fta.senior.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class OperationRecord(val time: Long, val title: String, val detail: String)

class OperationHistory(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    @Synchronized fun read(): List<OperationRecord> = runCatching {
        val data = JSONArray(prefs.getString("records", "[]"))
        (0 until data.length()).map { i ->
            val item = data.getJSONObject(i)
            OperationRecord(item.getLong("time"), item.getString("title"), item.getString("detail"))
        }
    }.getOrDefault(emptyList())

    @Synchronized fun append(title: String, detail: String) {
        val data = JSONArray()
        (listOf(OperationRecord(System.currentTimeMillis(), title, detail.take(12000))) + read())
            .take(50).forEach {
                data.put(JSONObject().put("time", it.time).put("title", it.title).put("detail", it.detail))
            }
        prefs.edit().putString("records", data.toString()).apply()
    }

    @Synchronized fun clear() { prefs.edit().remove("records").apply() }
}
