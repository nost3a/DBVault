package com.dbvault.app.db;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 用 Android Keystore 里的 AES 密钥加密本地保存的密码，
 * 明文不落盘
 * Stored credentials are encrypted with a Keystore-backed AES key;
 * plaintext never touches disk.
 */
public final class SecretBox {

    private static final String TAG = "SecretBox";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "dbvault.master.v1";
    private static final String PREFIX = "v1:";

    private static SecretKey cached;
    private static boolean unavailable;

    private SecretBox() {
    }

    private static SecretKey key() {
        if (cached != null) return cached;
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            if (ks.containsAlias(ALIAS)) {
                cached = (SecretKey) ks.getKey(ALIAS, null);
                return cached;
            }
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            cached = kg.generateKey();
            return cached;
        } catch (Exception e) {
            Log.w(TAG, "keystore unavailable, falling back to plain storage", e);
            unavailable = true;
            return null;
        }
    }

    public static String seal(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        SecretKey k = key();
        if (k == null) return plain;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, k);
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] ivAndCt = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, ivAndCt, 0, iv.length);
            System.arraycopy(ct, 0, ivAndCt, iv.length, ct.length);
            return PREFIX + Base64.encodeToString(ivAndCt, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.w(TAG, "seal failed", e);
            return plain;
        }
    }

    public static String open(String sealed) {
        if (sealed == null || sealed.isEmpty()) return "";
        if (!sealed.startsWith(PREFIX)) return sealed;
        SecretKey k = key();
        if (k == null) return sealed;
        try {
            byte[] all = Base64.decode(sealed.substring(PREFIX.length()), Base64.NO_WRAP);
            byte[] iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, 12);
            byte[] ct = new byte[all.length - 12];
            System.arraycopy(all, 12, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "open failed", e);
            return "";
        }
    }
}
