package app.rodalog.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception("HTTP $code: $message")

/** Cliente mínimo de la API REST de Google Sheets v4. */
class SheetsApi(private val token: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
    private val base = "https://sheets.googleapis.com/v4/spreadsheets"
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private suspend fun call(method: String, url: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val rb = when {
                body != null -> body.toString().toRequestBody(json)
                method == "GET" -> null
                else -> "{}".toRequestBody(json)
            }
            val req = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .method(method, rb)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw ApiException(resp.code, text.take(300))
                if (text.isBlank()) JSONObject() else JSONObject(text)
            }
        }

    private fun toJson(rows: List<List<Any?>>): JSONArray {
        val out = JSONArray()
        for (r in rows) {
            val a = JSONArray()
            for (c in r) a.put(c ?: "")
            out.put(a)
        }
        return out
    }

    suspend fun createSpreadsheet(title: String, tabs: List<String>): String {
        val sheets = JSONArray()
        for (t in tabs) sheets.put(JSONObject().put("properties", JSONObject().put("title", t)))
        val body = JSONObject()
            .put("properties", JSONObject().put("title", title))
            .put("sheets", sheets)
        return call("POST", base, body).getString("spreadsheetId")
    }

    suspend fun tabTitles(id: String): Set<String> {
        val r = call("GET", "$base/$id?fields=sheets.properties.title")
        val arr = r.optJSONArray("sheets") ?: return emptySet()
        val out = HashSet<String>()
        for (i in 0 until arr.length()) out.add(arr.getJSONObject(i).getJSONObject("properties").getString("title"))
        return out
    }

    suspend fun addTabs(id: String, titles: List<String>) {
        if (titles.isEmpty()) return
        val reqs = JSONArray()
        for (t in titles) {
            reqs.put(JSONObject().put("addSheet", JSONObject().put("properties", JSONObject().put("title", t))))
        }
        call("POST", "$base/$id:batchUpdate", JSONObject().put("requests", reqs))
    }

    suspend fun getValues(id: String, tab: String): List<List<Any?>> {
        val r = call("GET", "$base/$id/values/${enc(tab)}?valueRenderOption=UNFORMATTED_VALUE")
        val arr = r.optJSONArray("values") ?: return emptyList()
        val out = ArrayList<List<Any?>>()
        for (i in 0 until arr.length()) {
            val row = arr.getJSONArray(i)
            val cells = ArrayList<Any?>()
            for (j in 0 until row.length()) {
                val v = row.get(j)
                cells.add(if (v == JSONObject.NULL) null else v)
            }
            out.add(cells)
        }
        return out
    }

    suspend fun updateRange(id: String, range: String, rows: List<List<Any?>>) {
        val body = JSONObject().put("range", range).put("majorDimension", "ROWS").put("values", toJson(rows))
        call("PUT", "$base/$id/values/${enc(range)}?valueInputOption=RAW", body)
    }

    /** Varias escrituras en una sola llamada. */
    suspend fun batchUpdateValues(id: String, data: List<Pair<String, List<List<Any?>>>>) {
        if (data.isEmpty()) return
        val arr = JSONArray()
        for ((range, rows) in data) {
            arr.put(JSONObject().put("range", range).put("majorDimension", "ROWS").put("values", toJson(rows)))
        }
        val body = JSONObject().put("valueInputOption", "RAW").put("data", arr)
        call("POST", "$base/$id/values:batchUpdate", body)
    }

    suspend fun append(id: String, tab: String, rows: List<List<Any?>>) {
        if (rows.isEmpty()) return
        val body = JSONObject().put("majorDimension", "ROWS").put("values", toJson(rows))
        call(
            "POST",
            "$base/$id/values/${enc(tab)}:append?valueInputOption=RAW&insertDataOption=INSERT_ROWS",
            body
        )
    }
}
