// core/network/TokenManager.kt
//
// iOS Core/Network/TokenManager.swift (Keychain) 를 옮긴 파일.
// Android 에는 Keychain 이 없어서, Android Keystore(기기 보안 영역)의 키로 암호화해서 SharedPreferences 에 저장해요.

package com.naleul.naleul.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.naleul.naleul.core.util.AppContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object TokenManager {
    private const val ACCESS_TOKEN_KEY = "naleul.accessToken"
    private const val REFRESH_TOKEN_KEY = "naleul.refreshToken"
    private const val USER_ID_KEY = "naleul.userId"
    /** 로그인 없이 둘러보기 중인지 ("1"). 둘러보기 토큰은 읽기만 되고 refresh token 이 없어요 */
    private const val GUEST_KEY = "naleul.guest"
    /** 마지막으로 확인한 사용자 정보 — 앱을 켤 때 서버가 잠깐 안 돼도 로그인 화면으로 튕기지 않게 */
    private const val CACHED_USER_KEY = "naleul.cachedUser"

    private val storage by lazy { SecureStorage(AppContext.app) }
    private val plainPrefs by lazy { AppContext.app.getSharedPreferences("naleul_prefs", Context.MODE_PRIVATE) }

    // MARK: - Save

    fun saveAccessToken(token: String) = storage.put(ACCESS_TOKEN_KEY, token)
    fun saveRefreshToken(token: String) = storage.put(REFRESH_TOKEN_KEY, token)
    fun saveUserId(userId: Int) = storage.put(USER_ID_KEY, userId.toString())

    fun save(accessToken: String, refreshToken: String) {
        saveAccessToken(accessToken)
        saveRefreshToken(refreshToken)
        storage.remove(GUEST_KEY) // 진짜 로그인 → 둘러보기 끝
    }

    fun save(accessToken: String, refreshToken: String, userId: Int) {
        save(accessToken, refreshToken)
        saveUserId(userId)
    }

    /** 둘러보기 토큰 저장 (refresh token 없음) */
    fun saveGuest(accessToken: String, userId: Int) {
        saveAccessToken(accessToken)
        storage.remove(REFRESH_TOKEN_KEY)
        saveUserId(userId)
        storage.put(GUEST_KEY, "1")
    }

    val isGuest: Boolean get() = storage.get(GUEST_KEY) == "1"

    // MARK: - 사용자 정보 캐시 (민감하지 않은 표시용이라 일반 SharedPreferences)

    data class CachedUser(val name: String, val email: String, val role: String)

    fun cacheUser(name: String, email: String, role: String) {
        plainPrefs.edit()
            .putString("$CACHED_USER_KEY.name", name)
            .putString("$CACHED_USER_KEY.email", email)
            .putString("$CACHED_USER_KEY.role", role)
            .apply()
    }

    val cachedUser: CachedUser?
        get() {
            val name = plainPrefs.getString("$CACHED_USER_KEY.name", null) ?: return null
            val role = plainPrefs.getString("$CACHED_USER_KEY.role", null) ?: return null
            return CachedUser(name, plainPrefs.getString("$CACHED_USER_KEY.email", "") ?: "", role)
        }

    // MARK: - Read

    val accessToken: String? get() = storage.get(ACCESS_TOKEN_KEY)
    val refreshToken: String? get() = storage.get(REFRESH_TOKEN_KEY)
    val userId: Int? get() = storage.get(USER_ID_KEY)?.toIntOrNull()

    // MARK: - Delete

    fun clearAll() {
        storage.remove(ACCESS_TOKEN_KEY)
        storage.remove(REFRESH_TOKEN_KEY)
        storage.remove(USER_ID_KEY)
        storage.remove(GUEST_KEY)
        plainPrefs.edit()
            .remove("$CACHED_USER_KEY.name")
            .remove("$CACHED_USER_KEY.email")
            .remove("$CACHED_USER_KEY.role")
            .apply()
    }
}

/**
 * 값을 Android Keystore 의 키로 암호화해서 SharedPreferences 에 저장해요. (iOS Keychain 역할)
 * 암호화 키는 기기 보안 영역에 있고 앱이 꺼내 볼 수 없어서, 저장 파일이 유출돼도 복호화할 수 없어요.
 */
class SecureStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun put(key: String, value: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            // 복호화에 필요한 IV(초기값)를 암호문 앞에 붙여 함께 저장
            val combined = cipher.iv + encrypted
            // commit(): 바로 디스크에 써요 — 토큰은 저장 직후 다른 요청이 읽을 수 있어야 해서
            prefs.edit().putString(key, Base64.encodeToString(combined, Base64.NO_WRAP)).commit()
        } catch (e: Exception) {
            prefs.edit().remove(key).commit()
        }
    }

    fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val combined = Base64.decode(stored, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val encrypted = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            // 백업 복원 등으로 저장 파일만 남고 Keystore 키가 사라진 경우 → 복호화 불가 → 없는 값으로 취급
            null
        }
    }

    fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "naleul_secure_prefs"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "naleul_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
