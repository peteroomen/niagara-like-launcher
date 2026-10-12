package io.github.peteroomen.launcher.data

import io.github.peteroomen.launcher.model.AppUsage
import io.github.peteroomen.launcher.model.Fav
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Favourites are stored as JSON: `["key", {"id": "f1", "name": "Travel", "apps": ["key", …]}, …]`. */
object FavJson {

    fun encode(favs: List<Fav>): String = JSONArray().apply {
        favs.forEach { fav ->
            when (fav) {
                is Fav.App -> put(fav.key)
                is Fav.Folder -> put(JSONObject().put("id", fav.id).put("name", fav.name).put("apps", JSONArray(fav.apps)))
            }
        }
    }.toString()

    fun decode(json: String?): List<Fav> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                when (val item = array.get(i)) {
                    is String -> Fav.App(item)
                    is JSONObject -> {
                        val apps = item.getJSONArray("apps")
                        Fav.Folder(item.getString("id"), item.getString("name"), (0 until apps.length()).map(apps::getString))
                    }
                    else -> null
                }
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** Usage as `{"key": [opened, searched, lastOpened], …}`. */
    fun encodeUsage(usage: Map<String, AppUsage>): String = JSONObject().apply {
        usage.forEach { (key, u) -> put(key, JSONArray().put(u.opened.toDouble()).put(u.searched.toDouble()).put(u.lastOpened)) }
    }.toString()

    fun decodeUsage(json: String?): Map<String, AppUsage> {
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { key ->
                val a = obj.getJSONArray(key)
                AppUsage(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getLong(2))
            }
        } catch (e: JSONException) {
            emptyMap()
        }
    }

    fun encodeMap(map: Map<String, String>): String = JSONObject(map).toString()

    fun decodeMap(json: String?): Map<String, String> {
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith(obj::getString)
        } catch (e: JSONException) {
            emptyMap()
        }
    }
}
