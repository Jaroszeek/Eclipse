package pl.eclipse.app.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import pl.eclipse.app.data.JSON
import java.util.Base64

@Serializable
data class Credentials(val email: String, val password: String)

private val Context.credentialsDataStore by preferencesDataStore("credentials")
private val CIPHERTEXT = stringPreferencesKey("ciphertext")
private val ASSOCIATED_DATA = "eclipse-credentials".toByteArray()

/**
 * Dane logowania zaszyfrowane Tinkiem (AES-256-GCM); klucz Tinka chroni klucz główny w Android Keystore (SPEC 10.3).
 * Pliki `credentials` i `eclipse_keyset` są wyłączone z kopii zapasowej (`data_extraction_rules.xml`).
 */
class CredentialStore(private val context: Context) {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "keyset", "eclipse_keyset")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://eclipse_master_key")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    val hasCredentials = context.credentialsDataStore.data.map { it[CIPHERTEXT] != null }

    suspend fun read(): Credentials? {
        val encoded = context.credentialsDataStore.data.first()[CIPHERTEXT] ?: return null
        return runCatching {
            val plain = aead.decrypt(Base64.getDecoder().decode(encoded), ASSOCIATED_DATA)
            JSON.decodeFromString(Credentials.serializer(), plain.decodeToString())
        }.getOrNull()
    }

    suspend fun save(credentials: Credentials) {
        val plain = JSON.encodeToString(Credentials.serializer(), credentials).toByteArray()
        val encoded = Base64.getEncoder().encodeToString(aead.encrypt(plain, ASSOCIATED_DATA))
        context.credentialsDataStore.edit { it[CIPHERTEXT] = encoded }
    }

    suspend fun clear() {
        context.credentialsDataStore.edit { it.clear() }
    }
}
