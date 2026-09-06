package com.jobhub.backup;

import com.jobhub.backup.application.EncryptionService;
import com.jobhub.backup.application.EncryptionService.EncryptedPayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {
	private final EncryptionService enc = new EncryptionService();

	@Test
	void encryptThenDecryptRoundTrip() {
		byte[] plain = "hello 备份".getBytes(StandardCharsets.UTF_8);
		EncryptedPayload payload = enc.encrypt(plain, "test1234");
		assertArrayEquals(plain, enc.decrypt(payload, "test1234"));
	}

	@Test
	void differentInvocationsProduceDifferentCiphertext() {
		byte[] plain = "data".getBytes(StandardCharsets.UTF_8);
		EncryptedPayload a = enc.encrypt(plain, "pw");
		EncryptedPayload b = enc.encrypt(plain, "pw");
		assertFalse(java.util.Arrays.equals(a.ciphertext(), b.ciphertext()),
			"随机 salt/iv 应使相同明文产生不同密文");
	}

	@Test
	void wrongPassphraseFailsToDecrypt() {
		byte[] plain = "data".getBytes(StandardCharsets.UTF_8);
		EncryptedPayload payload = enc.encrypt(plain, "correct");
		assertThrows(Exception.class, () -> enc.decrypt(payload, "wrong"));
	}

	@Test
	void saltAndIvHaveExpectedLength() {
		EncryptedPayload p = enc.encrypt(new byte[] {1}, "x");
		assertEquals(16, p.salt().length);
		assertEquals(12, p.iv().length);
	}
}
