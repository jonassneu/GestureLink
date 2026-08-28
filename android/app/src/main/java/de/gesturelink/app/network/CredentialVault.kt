package de.gesturelink.app.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.credentialDataStore by preferencesDataStore("gesturelink_credentials")

data class StoredConnection(val clientId: String, val serverId: String, val host: String, val port: Int, val fingerprint: String, val credential: String)

class CredentialVault(private val context: Context) {
    private val alias = "gesturelink-device-credential-v1"
    private val encrypted = stringPreferencesKey("credential_ciphertext")
    private val iv = stringPreferencesKey("credential_iv")
    private val clientId = stringPreferencesKey("client_id")
    private val serverId = stringPreferencesKey("server_id")
    private val host = stringPreferencesKey("host")
    private val port = stringPreferencesKey("port")
    private val fingerprint = stringPreferencesKey("fingerprint")

    suspend fun save(pairing: PairingUri, id: String, credential: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(credential.toByteArray(Charsets.UTF_8))
        context.credentialDataStore.edit { preferences ->
            preferences[encrypted] = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
            preferences[iv] = android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP)
            preferences[clientId] = id
            preferences[serverId] = pairing.serverId
            preferences[host] = pairing.host
            preferences[port] = pairing.port.toString()
            preferences[fingerprint] = pairing.fingerprint
        }
    }

    suspend fun load(): StoredConnection? {
        val preferences = context.credentialDataStore.data.first()
        val ciphertext = preferences[encrypted] ?: return null
        val nonce = preferences[iv] ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, android.util.Base64.decode(nonce, android.util.Base64.NO_WRAP)))
        val credential = cipher.doFinal(android.util.Base64.decode(ciphertext, android.util.Base64.NO_WRAP)).toString(Charsets.UTF_8)
        return StoredConnection(
            clientId = preferences[clientId] ?: return null,
            serverId = preferences[serverId] ?: return null,
            host = preferences[host] ?: return null,
            port = preferences[port]?.toIntOrNull() ?: return null,
            fingerprint = preferences[fingerprint] ?: return null,
            credential = credential,
        )
    }

    suspend fun clear() = context.credentialDataStore.edit { it.clear() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }
}

