package com.reactnativemsal

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ReadableMapUtils {
    @Throws(JSONException::class)
    fun toJsonObject(readableMap: ReadableMap): JSONObject {
        val jsonObject = JSONObject()

        val iterator = readableMap.keySetIterator()

        while (iterator.hasNextKey()) {
            val key = iterator.nextKey()
            val type = readableMap.getType(key)

            when (type) {
                ReadableType.Null -> jsonObject.put(key, null)
                ReadableType.Boolean -> jsonObject.put(key, readableMap.getBoolean(key))
                ReadableType.Number -> jsonObject.put(key, readableMap.getDouble(key))
                ReadableType.String -> jsonObject.put(key, readableMap.getString(key))
                ReadableType.Map -> jsonObject.put(
                    key,
                    toJsonObject(
                        readableMap.getMap(key)!!
                    )
                )

                ReadableType.Array -> jsonObject.put(
                    key, toJsonArray(readableMap.getArray(key)!!)
                )
            }
        }

        return jsonObject
    }

    @Throws(JSONException::class)
    fun toJsonArray(readableArray: ReadableArray): JSONArray {
        val jsonArray = JSONArray()

        for (i in 0..<readableArray.size()) {
            val type = readableArray.getType(i)

            when (type) {
                ReadableType.Null -> jsonArray.put(i, null)
                ReadableType.Boolean -> jsonArray.put(i, readableArray.getBoolean(i))
                ReadableType.Number -> jsonArray.put(i, readableArray.getDouble(i))
                ReadableType.String -> jsonArray.put(i, readableArray.getString(i))
                ReadableType.Map -> jsonArray.put(
                    i,
                    toJsonObject(readableArray.getMap(i)!!)
                )

                ReadableType.Array -> jsonArray.put(
                    i, toJsonArray(readableArray.getArray(i)!!)
                )
            }
        }

        return jsonArray
    }

    fun getStringOrDefault(map: ReadableMap?, key: String, defaultValue: String): String {
        return try {
            getStringOrThrow(map, key)
        } catch (_: Exception) {
            defaultValue
        }
    }

    fun getStringOrThrow(map: ReadableMap?, key: String): String {
        requireNotNull(map) { "Map is null" }

        return map.getString(key)
            ?: throw NoSuchElementException("$key doesn't exist on map or is null")
    }
}
