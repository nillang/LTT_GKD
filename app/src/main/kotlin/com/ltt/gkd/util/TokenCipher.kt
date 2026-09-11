package com.ltt.gkd.util // 包声明：本文件属于工具包 com.ltt.gkd.util

import android.security.keystore.KeyGenParameterSpec // 导入 KeyGenParameterSpec，用于构建密钥生成参数规范
import android.security.keystore.KeyProperties // 导入 KeyProperties，提供加密算法/模式/用途等常量
import android.util.Base64 // 导入 Base64，用于字节数组与字符串互转
import java.security.KeyStore // 导入 KeyStore，用于管理密钥库条目
import javax.crypto.Cipher // 导入 Cipher，提供加解密能力
import javax.crypto.KeyGenerator // 导入 KeyGenerator，用于生成对称密钥
import javax.crypto.SecretKey // 导入 SecretKey，对称密钥接口
import javax.crypto.spec.GCMParameterSpec // 导入 GCMParameterSpec，GCM 解密所需的 IV 与认证标签长度参数

/**
 * Token 加密工具：使用 Android Keystore + AES-GCM。
 *
 * - 密钥存储在硬件安全区域（TEE/StrongBox），应用进程不可读取
 * - 加密结果以 Base64(iv + ciphertext) 形式存入 DataStore
 * - 降级：若 Keystore 不可用（如设备不支持），返回明文，不阻塞功能
 */
object TokenCipher {

    private const val KEYSTORE_ALIAS = "ltt_gkd_token_key" // Keystore 中密钥的别名，固定值保证加解密使用同一密钥
    private const val TRANSFORMATION = "AES/GCM/NoPadding" // 加密算法/模式/填充：AES-GCM 无填充
    private const val GCM_TAG_BITS = 128 // GCM 认证标签位数（128 位为最强强度）
    private const val IV_BYTES = 12 // GCM 推荐 IV 长度 12 字节（96 位）

    /**
     * 获取或创建 Keystore 中的 AES 密钥。
     *
     * 流程：先尝试从 AndroidKeyStore 读取已有密钥；若不存在则调用 [generateKey] 生成。
     * 任何异常（如设备不支持 Keystore）均返回 null，调用方据此降级为明文存储。
     *
     * @return 已有或新建的 [SecretKey]；失败返回 null
     */
    private fun getOrCreateKey(): SecretKey? = try { // 入口：尝试获取或生成密钥，表达式函数体
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) } // 获取 AndroidKeyStore 实例并初始化加载
        (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey // 读取已有密钥条目并取出 secretKey
            ?: generateKey() // 不存在则生成新密钥
    } catch (e: Exception) {
        com.ltt.gkd.util.Logger.w("Keystore 密钥获取失败，Token 将明文存储", e) // 异常时打 warn 日志
        null // 返回 null 触发上层降级
    }

    /**
     * 在 AndroidKeyStore 中生成新的 AES-256-GCM 密钥。
     *
     * 密钥用途为加密 + 解密，使用 GCM 模式、无填充，密钥长度 256 位。
     * 生成的密钥由 Keystore 托管，私钥材料不可被应用进程读出。
     *
     * @return 新生成的 [SecretKey]
     */
    private fun generateKey(): SecretKey { // 入口：生成新 AES-256-GCM 密钥
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore") // 创建 AES 密钥生成器，绑定 AndroidKeyStore provider
        gen.init( // 配置密钥生成参数
            KeyGenParameterSpec.Builder( // 构造参数规范
                KEYSTORE_ALIAS, // 绑定别名
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT // 用途：加密 + 解密
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM) // 设置 GCM 分组模式
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE) // 设置无填充
                .setKeySize(256) // 密钥长度 256 位
                .build() // 构建参数规范
        )
        return gen.generateKey() // 生成并返回密钥
    }

    /**
     * 加密 Token。
     *
     * 使用 AES-GCM 加密明文，IV 由 Cipher 自动生成，最终返回 `Base64(iv + 密文)` 字符串。
     * 当 Keystore 不可用或加密异常时降级返回原明文，保证不阻塞上层流程。
     *
     * @param plain 待加密的明文 Token
     * @return Base64 编码的 `iv + 密文`；降级时返回原 [plain]
     */
    fun encrypt(plain: String): String { // 入口：加密 Token
        if (plain.isEmpty()) return "" // 空字符串直接返回，无需加密
        val key = getOrCreateKey() ?: return plain // 获取密钥，拿不到则降级返回明文
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION) // 创建 AES/GCM/NoPadding Cipher
            cipher.init(Cipher.ENCRYPT_MODE, key) // 初始化为加密模式
            val iv = cipher.iv // GCM 每次加密生成随机 IV
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8)) // 加密明文为字节数组
            val combined = iv + ct // 拼接 IV 与密文，便于解密时一并取回
            Base64.encodeToString(combined, Base64.NO_WRAP) // Base64 编码（无换行）
        } catch (e: Exception) {
            com.ltt.gkd.util.Logger.w("Token 加密失败，明文存储", e) // 加密失败时打 warn 日志
            plain // 降级返回明文
        }
    }

    /**
     * 解密 Token。
     *
     * 输入为 [encrypt] 生成的 `Base64(iv + 密文)`；解密后返回明文。
     * 若输入长度不足 IV 大小，则判定为旧版明文存储直接返回；
     * 其他异常（如密钥变更）也降级返回原字符串。
     *
     * @param stored 存储中的 Base64 字符串或旧版明文
     * @return 解密后的明文；降级时返回原 [stored]
     */
    fun decrypt(stored: String): String { // 入口：解密 Token
        if (stored.isEmpty()) return "" // 空字符串直接返回
        val key = getOrCreateKey() ?: return stored // 获取密钥失败则原样返回
        return try {
            val combined = Base64.decode(stored, Base64.NO_WRAP) // Base64 解码为字节数组
            if (combined.size < IV_BYTES) return stored // 不是加密格式，当作明文
            val iv = combined.copyOfRange(0, IV_BYTES) // 前 12 字节为 IV
            val ct = combined.copyOfRange(IV_BYTES, combined.size) // 其余为密文
            val cipher = Cipher.getInstance(TRANSFORMATION) // 创建 Cipher
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv)) // 初始化为解密模式并传入 IV 与认证标签长度
            String(cipher.doFinal(ct), Charsets.UTF_8) // 解密并按 UTF-8 转字符串
        } catch (e: Exception) {
            // 可能是旧版明文存储，直接返回
            stored // 返回原字符串作为降级结果
        }
    }
}
