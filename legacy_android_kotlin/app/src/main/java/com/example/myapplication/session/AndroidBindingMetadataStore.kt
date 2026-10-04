package com.example.myapplication.session

import android.content.Context
import com.example.myapplication.BuildConfig
import com.example.myapplication.security.SecurityManager

class AndroidBindingMetadataStore(
    context: Context,
    preferencesName: String = "device_binding_recovery",
) : BindingMetadataStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName, Context.MODE_PRIVATE,
    )

    override fun read(): Map<String, *> = preferences.all

    override fun write(values: Map<String, String>): Boolean {
        val editor = preferences.edit().clear()
        values.forEach { (name, value) -> editor.putString(name, value) }
        return editor.commit()
    }

    override fun clear(): Boolean = preferences.edit().clear().commit()
}

object SessionRecoveryProvider {
    @Volatile private var instance: SessionRecovery? = null

    fun get(context: Context): SessionRecovery = instance ?: synchronized(this) {
        instance ?: SessionRecovery(
            AndroidBindingMetadataStore(context),
            SecurityManager()::lookupExistingKey,
            BuildConfig.API_BASE_URL,
        ).also { instance = it }
    }
}
