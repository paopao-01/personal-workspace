package com.jobhub.backup.application;

import com.jobhub.common.error.BusinessRuleException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * AES-256-GCM 加密 + PBKDF2WithHmacSHA256 密钥派生。
 * passphrase 与派生密钥永不持久化；salt 与 iv 随记录落库以供恢复。
 */
@Component
public class EncryptionService {
	static final int SALT_BYTES = 16;
	static final int IV_BYTES = 12;
	static final int KEY_BITS = 256;
	static final int ITERATIONS = 100_000;
	static final int TAG_BITS = 128;
	private static final String KDF = "PBKDF2WithHmacSHA256";
	private static final String CIPHER = "AES/GCM/NoPadding";

	public record EncryptedPayload(byte[] salt, byte[] iv, byte[] ciphertext) { }

	private final SecureRandom random = new SecureRandom();

	public EncryptedPayload encrypt(byte[] plaintext, String passphrase) {
		try {
			byte[] salt = new byte[SALT_BYTES];
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(salt);
			random.nextBytes(iv);
			SecretKey key = deriveKey(passphrase, salt, ITERATIONS);
			Cipher cipher = Cipher.getInstance(CIPHER);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext);
			return new EncryptedPayload(salt, iv, ciphertext);
		} catch (Exception ex) {
			throw new BusinessRuleException("加密失败：" + ex.getMessage());
		}
	}

	public byte[] decrypt(EncryptedPayload payload, String passphrase) {
		try {
			SecretKey key = deriveKey(passphrase, payload.salt(), ITERATIONS);
			Cipher cipher = Cipher.getInstance(CIPHER);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload.iv()));
			return cipher.doFinal(payload.ciphertext());
		} catch (Exception ex) {
			throw new BusinessRuleException("解密失败：" + ex.getMessage());
		}
	}

	private SecretKey deriveKey(String passphrase, byte[] salt, int iterations) throws Exception {
		PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS);
		SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF);
		byte[] keyBytes = factory.generateSecret(spec).getEncoded();
		return new SecretKeySpec(keyBytes, "AES");
	}
}
