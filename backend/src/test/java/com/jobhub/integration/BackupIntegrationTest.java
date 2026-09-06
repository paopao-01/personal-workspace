package com.jobhub.integration;

import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-36 加密备份：手动触发生成、列表、下载与 passphrase 校验。
 * 复用标准数据包导出 → PBKDF2 派生 AES-256-GCM 加密落盘；passphrase 不回显、不落库。
 */
class BackupIntegrationTest extends AbstractIntegrationTest {

	@Test
	void AT36_encryptedBackupCreateListDownload() throws Exception {
		// 先造一个岗位，保证导出有可导出数据
		String jobBody = TestFixtures.createJobBody("示例科技", "Java 后端");
		restTemplate.postForEntity(url("/jobs"), TestFixtures.httpJson(jobBody), String.class);

		// 生成加密备份
		String reqBody = "{\"passphrase\":\"test1234\"}";
		ResponseEntity<String> created = restTemplate.exchange(url("/backups"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String body = created.getBody();
		assertThat(body).isNotNull();
		String id = JsonProbe.str(body, "id");
		String fileName = JsonProbe.str(body, "fileName");
		Long sizeBytes = JsonProbe.lng(body, "sizeBytes");
		assertThat(id).isNotNull();
		assertThat(fileName).isEqualTo(id + ".enc");
		assertThat(sizeBytes).isNotNull().isPositive();
		assertThat(JsonProbe.str(body, "algorithm")).isEqualTo("AES_256_GCM_PBKDF2");
		assertThat(JsonProbe.intVal(body, "pbkdf2Iterations")).isEqualTo(100_000);
		assertThat(JsonProbe.str(body, "dataExportId")).isNotNull();
		// passphrase 永不回显
		assertThat(body).doesNotContain("passphrase");

		// 备份文件落盘且大小一致
		Path file = Paths.get("./target/backups", fileName);
		assertThat(Files.exists(file)).isTrue();
		assertThat(Files.size(file)).isEqualTo(sizeBytes);

		// salt+iv 落库，passphrase/派生密钥不落库
		byte[] salt = jdbc.queryForObject("SELECT salt FROM backup_record WHERE id = ?", byte[].class, id);
		byte[] iv = jdbc.queryForObject("SELECT iv FROM backup_record WHERE id = ?", byte[].class, id);
		assertThat(salt).hasSize(16);
		assertThat(iv).hasSize(12);
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();

		// 列表最新优先
		String list = restTemplate.getForEntity(url("/backups"), String.class).getBody();
		assertThat(list).isNotNull();
		assertThat(JsonProbe.arraySize(list, "")).isGreaterThanOrEqualTo(1);
		assertThat(list).contains(id);

		// 下载字节数等于 sizeBytes
		ResponseEntity<byte[]> dl = restTemplate.getForEntity(url("/backups/" + id + "/download"), byte[].class);
		assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(dl.getBody()).hasSize(sizeBytes.intValue());
		assertThat(dl.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
	}

	@Test
	void shortPassphraseReturns400() {
		String reqBody = "{\"passphrase\":\"short\"}";
		ResponseEntity<String> bad = restTemplate.exchange(url("/backups"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void downloadMissingReturns404() {
		ResponseEntity<String> missing = restTemplate.getForEntity(url("/backups/99999999-9999-9999-9999-999999999999/download"),
			String.class);
		assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
