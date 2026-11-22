package app.retrogram.xmpp;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.telegram.messenger.FileLog;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts XMPP passwords at rest with a key that lives in the Android Keystore (AES-GCM, not extractable).
 * If the keystore is unusable the value is stored with a "p0:" prefix (base64 only) instead of failing:
 * losing the ability to log in is worse than a weaker store, and the app's private storage is still sandboxed.
 */
final class XmppSecrets {
    private static final String ALIAS = "retrogram_xmpp_v1";
    private static final String PROVIDER = "AndroidKeyStore";

    private XmppSecrets() {
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return (SecretKey) ks.getKey(ALIAS, null);
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }

    static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        byte[] data = plain.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = c.doFinal(data);
            return "v1:" + Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
        } catch (Throwable e) {
            FileLog.e(e);
            return "p0:" + Base64.encodeToString(data, Base64.NO_WRAP);
        }
    }

    static String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        try {
            if (stored.startsWith("p0:")) {
                return new String(Base64.decode(stored.substring(3), Base64.NO_WRAP), StandardCharsets.UTF_8);
            }
            if (stored.startsWith("v1:")) {
                String[] parts = stored.split(":");
                byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
                byte[] ct = Base64.decode(parts[2], Base64.NO_WRAP);
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
                return new String(c.doFinal(ct), StandardCharsets.UTF_8);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return "";
    }
}
