package com.rivo.studio.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.rivo.studio.core.model.ProviderConfig
import com.rivo.studio.core.model.ProviderKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, app-private provider configuration. The API key never enters Room or logs. */
class EncryptedProviderVault(context: Context) {
    private val file = File(context.noBackupFilesDir, "provider-vault.bin")

    @Synchronized
    fun readAll(): List<ProviderConfig> {
        if (!file.exists()) return emptyList()
        val encoded = file.readBytes()
        require(encoded.size > NONCE_BYTES + TAG_BYTES) { "Provider vault data is incomplete." }
        val input = ByteBuffer.wrap(encoded)
        val nonce = ByteArray(NONCE_BYTES).also(input::get)
        val cipherText = ByteArray(input.remaining()).also(input::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, nonce))
        val plain = cipher.doFinal(cipherText)
        val array = JSONArray(plain.toString(Charsets.UTF_8))
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ProviderConfig(
                id = item.getString("id"),
                name = item.getString("name"),
                kind = ProviderKind.valueOf(item.getString("kind")),
                baseUrl = item.getString("baseUrl"),
                apiKey = item.getString("apiKey"),
                model = item.getString("model"),
                isDefault = item.optBoolean("isDefault", false),
                updatedAt = item.optLong("updatedAt", 0L),
            )
        }
    }

    @Synchronized
    fun writeAll(configs: List<ProviderConfig>) {
        if (configs.isEmpty()) {
            file.delete()
            return
        }
        val array = JSONArray()
        configs.forEach { config ->
            array.put(
                JSONObject()
                    .put("id", config.id)
                    .put("name", config.name)
                    .put("kind", config.kind.name)
                    .put("baseUrl", config.baseUrl)
                    .put("apiKey", config.apiKey)
                    .put("model", config.model)
                    .put("isDefault", config.isDefault)
                    .put("updatedAt", config.updatedAt),
            )
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        val output = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { stream ->
            stream.write(output)
            stream.fd.sync()
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IllegalStateException("Unable to persist provider settings safely.")
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rivo_provider_vault_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        const val TAG_BITS = TAG_BYTES * 8
    }
}
