package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class ExportIntegrationTest extends AbstractIntegrationTest {

	private static final String EXPORT_KEY = TestFixtures.newKey();

	@Test
	void AT24_exportContainsBusinessDataAndExcludesOperationalRecords() {
		// 用固定的幂等键创建业务数据，用于断言导出不包含幂等记录
		String job = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createJobBody("导出验证科技", "AT24 岗位"),
				"Idempotency-Key", EXPORT_KEY), String.class).getBody();
		String jobId = JsonProbe.str(job, "id");
		String evidence = restTemplate.exchange(url("/evidence"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("""
				{"type":"GIT_REPOSITORY","title":"AT24 证据","urlOrPath":"https://github.com/user/at24"}
				""", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String evidenceId = JsonProbe.str(evidence, "id");
		restTemplate.exchange(url("/evidence/" + evidenceId + "/attachments"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"displayName\":\"AT24附件\",\"sourceType\":\"EXTERNAL_URL\",\"location\":\"https://example.com/at24.pdf\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class);

		ResponseEntity<String> created = restTemplate.exchange(url("/data-exports"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"format\":\"JSON\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(JsonProbe.str(created.getBody(), "status")).isEqualTo("SUCCEEDED");
		assertThat(JsonProbe.str(created.getBody(), "downloadUrl"))
			.isEqualTo("/api/data-exports/" + JsonProbe.str(created.getBody(), "id") + "/download");
		String exportId = JsonProbe.str(created.getBody(), "id");

		String detail = restTemplate.getForEntity(url("/data-exports/" + exportId), String.class).getBody();
		assertThat(JsonProbe.str(detail, "status")).isEqualTo("SUCCEEDED");

		ResponseEntity<String> download = restTemplate.getForEntity(
			url("/data-exports/" + exportId + "/download"), String.class);
		assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
		String payload = download.getBody();

		// 导出包含业务数据及关联 ID
		assertThat(payload).contains("导出验证科技");
		assertThat(payload).contains(jobId);
		assertThat(payload).contains("https://github.com/user/at24");
		assertThat(payload).contains("job_posting");
		assertThat(payload).contains("evidence");
		assertThat(payload).contains(evidenceId);
		assertThat(payload).contains("evidence_attachment");
		assertThat(payload).contains("https://example.com/at24.pdf");

		// 导出排除运行记录与幂等数据
		assertThat(payload).doesNotContain(EXPORT_KEY);
		assertThat(payload).doesNotContain("\"idempotency_record\"");
		assertThat(payload).doesNotContain("\"audit_log\"");
		assertThat(payload).doesNotContain("\"trash_item\"");
		assertThat(payload).doesNotContain("\"data_export\"");
		assertThat(payload).doesNotContain("\"idempotency_key\"");
	}

	@Test
	void AT24_exportRejectsUnsupportedFormatAndHidesMissingFile() {
		// P1 起 CSV 为合法格式（见 P1_csvExport…），不支持格式改为 XML
		ResponseEntity<String> invalidFormat = restTemplate.exchange(url("/data-exports"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"format\":\"XML\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		assertThat(invalidFormat.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(invalidFormat.getBody()).contains("VALIDATION_ERROR");

		ResponseEntity<String> missingExport = restTemplate.getForEntity(
			url("/data-exports/99999999-9999-9999-9999-999999999999"), String.class);
		assertThat(missingExport.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<String> missingFile = restTemplate.getForEntity(
			url("/data-exports/99999999-9999-9999-9999-999999999999/download"), String.class);
		assertThat(missingFile.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void P1_csvExportPackagesBusinessTablesAsZipWithoutIdempotencyKey() throws Exception {
		String job = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createJobBody("CSV导出科技", "P1 CSV 岗位"),
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String jobId = JsonProbe.str(job, "id");

		ResponseEntity<String> created = restTemplate.exchange(url("/data-exports"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"format\":\"CSV\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(JsonProbe.str(created.getBody(), "format")).isEqualTo("CSV");
		assertThat(JsonProbe.str(created.getBody(), "status")).isEqualTo("SUCCEEDED");
		String exportId = JsonProbe.str(created.getBody(), "id");

		ResponseEntity<byte[]> download = restTemplate.getForEntity(
			url("/data-exports/" + exportId + "/download"), byte[].class);
		assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(download.getHeaders().getContentType().toString()).isEqualTo("application/zip");

		// 解包 ZIP：全部业务表 CSV、job_posting 行数据在列、application_status_log 不含幂等键列
		java.util.Map<String, String> entries = new java.util.LinkedHashMap<>();
		try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
				new java.io.ByteArrayInputStream(download.getBody()))) {
			java.util.zip.ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String content = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				entries.put(entry.getName(), content);
			}
		}
		assertThat(entries).containsKey("job_posting.csv");
		assertThat(entries).containsKey("application_status_log.csv");
		assertThat(entries.size()).isGreaterThanOrEqualTo(23);

		String jobCsv = entries.get("job_posting.csv");
		assertThat(jobCsv).startsWith("\uFEFF"); // UTF-8 BOM
		assertThat(jobCsv).contains("company_name");
		assertThat(jobCsv).contains("CSV导出科技");
		assertThat(jobCsv).contains(jobId);
		// 表头行不含幂等键列（application_status_log 是唯一含该列的业务表）
		assertThat(entries.get("application_status_log.csv")).doesNotContain("idempotency_key");

		// 非法格式仍拒绝
		ResponseEntity<String> invalid = restTemplate.exchange(url("/data-exports"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"format\":\"XML\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}
}
