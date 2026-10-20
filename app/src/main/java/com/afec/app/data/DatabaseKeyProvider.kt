package com.afec.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * §11 Database Encryption: the SQLCipher passphrase is a random 32-byte secret,
 * itself encrypted with an Android Keystore AES-256-GCM key that never leaves
 * the secure hardware-backed keystore. Only the wrapped (encrypted) passphrase
 * is stored, in app-private SharedPreferences.
 */
object DatabaseKeyProvider {
    private const val PREFS = "afec_secure"
    private const val KEY_CIPHER = "db_key_cipher"
    private const val KEY_IV = "db_key_iv"
    private const val ALIAS = "afec_db_key"

    fun passphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cipherB64 = prefs.getString(KEY_CIPHER, null)
        val ivB64 = prefs.getString(KEY_IV, null)
        if (cipherB64 != null && ivB64 != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP))
            )
            return cipher.doFinal(Base64.decode(cipherB64, Base64.NO_WRAP))
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit()
            .putString(KEY_CIPHER, Base64.encodeToString(cipher.doFinal(fresh), Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        return fresh
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
