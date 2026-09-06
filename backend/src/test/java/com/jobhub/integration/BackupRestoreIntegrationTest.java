package com.jobhub.integration;

import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-37 加密备份恢复：上传 .enc 文件 + passphrase，解密后行级幂等恢复。
 * 验证：缺失行被插入、重复恢复幂等、错误 passphrase/损坏文件 422、短 passphrase 400。
 * passphrase 与派生密钥不回显、不落库。
 */
class BackupRestoreIntegrationTest extends AbstractIntegrationTest {

	private static final String PASSPHRASE = "test1234";

	/** 生成一份加密备份并返回落盘 .enc 文件的字节数组。 */
	private byte[] createBackupEncFile() throws Exception {
		// 先造一个岗位，保证导出有可导出数据
		String jobBody = TestFixtures.createJobBody("恢复示例", "Java 后端");
		restTemplate.postForEntity(url("/jobs"), TestFixtures.httpJson(jobBody), String.class);

		// 生成加密备份
		String reqBody = "{\"passphrase\":\"" + PASSPHRASE + "\"}";
		ResponseEntity<String> created = restTemplate.exchange(url("/backups"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String fileName = JsonProbe.str(created.getBody(), "fileName");

		Path file = Paths.get("./target/backups", fileName);
		assertThat(Files.exists(file)).isTrue();
		return Files.readAllBytes(file);
	}

	/** 上传 .enc 与 passphrase 调用恢复端点，返回响应（错误响应不抛异常，直接返回状态与体）。 */
	private ResponseEntity<String> restore(byte[] encBytes, String passphrase) {
		// 用 RestClient 的 exchange 捕获 4xx/5xx 响应体而非抛异常
		RestClient client = RestClient.builder().build();
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("file", new ByteArrayResource(encBytes) {
			@Override
			public String getFilename() {
				return "backup.enc";
			}
		});
		parts.add("passphrase", passphrase);
		return client.post()
			.uri(url("/backups/restore"))
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(parts)
			.exchange((req, res) -> new ResponseEntity<>(res.bodyTo(String.class), res.getStatusCode()));
	}

	@Test
	void AT37_restoreInsertsMissingRowsAndIsIdempotent() throws Exception {
		// 1. 生成备份（此时数据库有 1 个岗位及若干候选要求）
		byte[] enc = createBackupEncFile();

		// 备份时 job_posting 应有 1 条；knowledge_point 等表因 DatabaseCleaner 不清可能累积，
		// 但不影响恢复语义验证。
		Integer jobsBeforeClear = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsBeforeClear).isEqualTo(1);

		// 2. 清空业务数据（模拟数据库丢失/部分清空）。注意 DatabaseCleaner 不清 knowledge_point，
		// 故备份中的 knowledge_point 行在恢复时会被识别为重复跳过——这正是行级幂等恢复的语义。
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");
		Integer jobsAfterClear = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsAfterClear).isZero();

		// 3. 恢复：缺失的 job_posting 行应被重新插入
		ResponseEntity<String> restored = restore(enc, PASSPHRASE);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = restored.getBody();
		assertThat(body).isNotNull();
		assertThat(body).doesNotContain("passphrase");
		assertThat(JsonProbe.str(body, "status")).isNotNull();
		int inserted = JsonProbe.intVal(body, "inserted");
		assertThat(inserted).isPositive();
		Integer jobsAfterRestore = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsAfterRestore).isEqualTo(1);

		// 4. 再次恢复：幂等——inserted=0（无新增行），job_posting 不产生重复行
		ResponseEntity<String> restoredAgain = restore(enc, PASSPHRASE);
		assertThat(restoredAgain.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body2 = restoredAgain.getBody();
		assertThat(JsonProbe.intVal(body2, "inserted")).isZero();
		// 第二次恢复的 skippedIdentical 至少覆盖第一次插入的行（job_posting + 已存在表）
		assertThat(JsonProbe.intVal(body2, "skippedIdentical")).isGreaterThanOrEqualTo(inserted);
		Integer jobsAfterSecond = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsAfterSecond).isEqualTo(1);

		// 5. passphrase/派生密钥不落库
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
	}

	@Test
	void wrongPassphraseReturns422() throws Exception {
		byte[] enc = createBackupEncFile();
		// 清空业务数据避免恢复插入
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		ResponseEntity<String> bad = restore(enc, "wrong-passphrase");
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		// 不进行任何插入
		Integer jobs = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobs).isZero();
	}

	@Test
	void truncatedFileReturns422() {
		// 不足 28 字节（缺 salt/iv）
		byte[] truncated = new byte[]{1, 2, 3};
		ResponseEntity<String> bad = restore(truncated, PASSPHRASE);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
	}

	@Test
	void shortPassphraseReturns400() throws Exception {
		byte[] enc = createBackupEncFile();
		// passphrase 短于 8 位 → 400
		ResponseEntity<String> bad = restore(enc, "short");
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}
}
