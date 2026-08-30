package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceAttachmentIntegrationTest extends AbstractIntegrationTest {

    @Test
    void P1_attachmentReferenceCrud_isVersionedSoftDeletedAndRestorable() {
        String evidence = restTemplate.exchange(url("/evidence"), HttpMethod.POST,
            TestFixtures.httpWithHeaders("{\"type\":\"LOAD_TEST_REPORT\",\"title\":\"压测证据\"}",
                "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
        String evidenceId = JsonProbe.str(evidence, "id");
        String body = """
            {"displayName":"压测报告 PDF","sourceType":"LOCAL_PATH",
             "location":"D:\\\\docs\\\\load-test.pdf","mediaType":"application/pdf",
             "sizeBytes":2048,"description":"面试前复习吞吐量结论"}
            """;
        String key = TestFixtures.newKey();
        ResponseEntity<String> created = restTemplate.exchange(url("/evidence/" + evidenceId + "/attachments"),
            HttpMethod.POST, TestFixtures.httpWithHeaders(body, "Idempotency-Key", key), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String attachmentId = JsonProbe.str(created.getBody(), "id");
        assertThat(JsonProbe.str(created.getBody(), "location")).isEqualTo("D:\\docs\\load-test.pdf");
        assertThat(JsonProbe.lng(created.getBody(), "sizeBytes")).isEqualTo(2048);

        ResponseEntity<String> replay = restTemplate.exchange(url("/evidence/" + evidenceId + "/attachments"),
            HttpMethod.POST, TestFixtures.httpWithHeaders(body, "Idempotency-Key", key), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(JsonProbe.str(replay.getBody(), "id")).isEqualTo(attachmentId);

        assertThat(JsonProbe.arraySize(restTemplate.getForEntity(url("/evidence-attachments?evidenceId=" + evidenceId), String.class).getBody(), ""))
            .isEqualTo(1);
        ResponseEntity<String> stale = restTemplate.exchange(url("/evidence-attachments/" + attachmentId), HttpMethod.PUT,
            TestFixtures.httpWithHeaders(body.replace("压测报告 PDF", "过期修改"), "Idempotency-Key", TestFixtures.newKey(),
                "If-Match-Version", "9"), String.class);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).contains("VERSION_CONFLICT");

        String updatedBody = body.replace("压测报告 PDF", "压测报告 PDF（修订）");
        ResponseEntity<String> updated = restTemplate.exchange(url("/evidence-attachments/" + attachmentId), HttpMethod.PUT,
            TestFixtures.httpWithHeaders(updatedBody, "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonProbe.lng(updated.getBody(), "version")).isEqualTo(1);

        ResponseEntity<String> deleted = restTemplate.exchange(url("/evidence-attachments/" + attachmentId), HttpMethod.DELETE,
            TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "1"), String.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(JsonProbe.arraySize(restTemplate.getForEntity(url("/evidence-attachments"), String.class).getBody(), ""))
            .isEqualTo(0);

        String trash = restTemplate.getForEntity(url("/trash"), String.class).getBody();
        String trashId = null;
        for (int i = 0; i < JsonProbe.arraySize(trash, ""); i++) {
            if (attachmentId.equals(JsonProbe.arrStr(trash, "", i, "resourceId"))) {
                trashId = JsonProbe.arrStr(trash, "", i, "id");
            }
        }
        assertThat(trashId).isNotBlank();
        ResponseEntity<String> restored = restTemplate.postForEntity(url("/trash/" + trashId + "/restore"), null, String.class);
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonProbe.arraySize(restTemplate.getForEntity(url("/evidence-attachments"), String.class).getBody(), ""))
            .isEqualTo(1);
    }

    @Test
    void P1_attachmentReference_rejectsUnknownEvidence() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/evidence/99999999-9999-9999-9999-999999999999/attachments"), HttpMethod.POST,
            TestFixtures.httpWithHeaders("{\"displayName\":\"孤立引用\",\"sourceType\":\"EXTERNAL_URL\",\"location\":\"https://example.com\"}",
                "Idempotency-Key", TestFixtures.newKey()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void P1_attachmentReference_importRestoresMetadataWithoutReadingContent() {
        String evidenceId = "e1111111-1111-4111-8111-e11111111111";
        String attachmentId = "a2222222-2222-4222-8222-a22222222222";
        String packageJson = """
            {"format":"JSON","exportedAt":"2026-08-30T00:00:00Z","tables":{
              "evidence":[{"id":"%s","type":"ARTICLE","title":"导入证据","created_at":"2026-08-30T00:00:00Z","updated_at":"2026-08-30T00:00:00Z","version":0}],
              "evidence_attachment":[{"id":"%s","evidence_id":"%s","display_name":"导入附件","source_type":"EXTERNAL_URL","location":"https://example.com/imported.pdf","media_type":"application/pdf","size_bytes":4096,"description":"导入的人工说明","created_at":"2026-08-30T00:00:00Z","updated_at":"2026-08-30T00:00:00Z","version":0}]
            }}
            """.formatted(evidenceId, attachmentId, evidenceId);
        ResponseEntity<String> restored = restTemplate.exchange(url("/data-imports/restore"), HttpMethod.POST,
            TestFixtures.httpWithHeaders(packageJson, "Idempotency-Key", TestFixtures.newKey()), String.class);
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonProbe.lng(restored.getBody(), "inserted")).isEqualTo(2);
        String attachments = restTemplate.getForEntity(url("/evidence-attachments?evidenceId=" + evidenceId), String.class).getBody();
        assertThat(JsonProbe.arraySize(attachments, "")).isEqualTo(1);
        assertThat(JsonProbe.arrStr(attachments, "", 0, "location")).isEqualTo("https://example.com/imported.pdf");
        assertThat(JsonProbe.arrLng(attachments, "", 0, "sizeBytes")).isEqualTo(4096);
    }
}
