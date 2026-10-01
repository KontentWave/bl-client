package com.example.myapplication.data.remote.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ApiMetaAdapter : JsonDeserializer<ApiMeta> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): ApiMeta {
        if (json == null || json.isJsonNull) {
            return ApiMeta()
        }

        if (json.isJsonArray) {
            return ApiMeta()
        }

        if (!json.isJsonObject) {
            throw JsonParseException("Expected 'meta' to be an object, array, or null.")
        }

        val jsonObject = json.asJsonObject
        val retryable = jsonObject.get("retryable")?.takeUnless { it.isJsonNull }?.asBoolean
        val upstreamStatus = jsonObject.get("upstream_status")?.takeUnless { it.isJsonNull }?.asInt

        return ApiMeta(
            retryable = retryable,
            upstreamStatus = upstreamStatus,
        )
    }
}

