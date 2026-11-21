package com.nextnotes.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** The user's AI profiles and which one is active, in app-private preferences. */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nextnotes_ai_profiles", Context.MODE_PRIVATE)

    fun profiles(): List<AiProfile> = decode(prefs.getString(KEY_LIST, null))

    fun active(): AiProfile? {
        val list = profiles()
        val id = prefs.getString(KEY_ACTIVE, null)
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    fun setActive(id: String) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Insert or replace by id. */
    fun save(profile: AiProfile) {
        val list = profiles().toMutableList()
        val i = list.indexOfFirst { it.id == profile.id }
        if (i >= 0) list[i] = profile else list += profile
        prefs.edit().putString(KEY_LIST, encode(list)).apply()
        if (prefs.getString(KEY_ACTIVE, null) == null) setActive(profile.id)
    }

    fun delete(id: String) {
        val list = profiles().filterNot { it.id == id }
        val edit = prefs.edit().putString(KEY_LIST, encode(list))
        if (prefs.getString(KEY_ACTIVE, null) == id) edit.remove(KEY_ACTIVE)
        edit.apply()
    }

    companion object {
        private const val KEY_LIST = "profiles"
        private const val KEY_ACTIVE = "active"

        fun newId(): String = UUID.randomUUID().toString()

        fun encode(list: List<AiProfile>): String = JSONArray().apply {
            for (p in list) put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("protocol", p.protocol.id)
                    .put("baseUrl", p.baseUrl)
                    .put("model", p.model)
                    .put("opencodeSession", p.opencodeSession)
                    .put("maxTokens", p.maxTokens),
            )
        }.toString()

        fun decode(raw: String?): List<AiProfile> {
            val array = runCatching { JSONArray(raw ?: return emptyList()) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                AiProfile(
                    id = o.optString("id").ifBlank { return@mapNotNull null },
                    name = o.optString("name"),
                    protocol = Protocol.fromId(o.optString("protocol")),
                    baseUrl = o.optString("baseUrl"),
                    model = o.optString("model"),
                    opencodeSession = o.optBoolean("opencodeSession", false),
                    // 4096 and 32000 were the old defaults, not choices: they give way to the model's own.
                    maxTokens = o.optInt("maxTokens", 0).takeUnless { it == 4096 || it == 32000 } ?: 0,
                )
            }
        }
    }
}
