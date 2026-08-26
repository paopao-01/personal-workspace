package com.jobhub.integration.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 测试夹具：JD 样例与请求体/请求头构造工具。
 *
 * JD 样例命中 RequirementExtractor 词典 ≥10 关键词（Java/Spring Boot/MyBatis/MySQL/Redis/Kafka/JVM/
 * 并发/分布式/微服务/Docker/Kubernetes/Linux/Spring/JPA/设计模式/数据结构/网络），稳定触发 ≥3 候选；
 * 含"负责/职责"(RESPONSIBILITY)、"5 年"(EXPERIENCE)、"优先"(BONUS) 类型推断线索。
 */
public final class TestFixtures {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** 所有请求复用的样例 JD（≥20 字符，命中 ≥10 词典别名）。 */
	public static final String SAMPLE_JD = """
			岗位名称：高级 Java 后端工程师
			公司：示例科技有限公司
			工作职责：
			- 负责核心交易系统的设计与开发，使用 Java 17 与 Spring Boot 3 构建微服务；
			- 参与高并发场景下的性能调优，熟悉 JVM 调优与多线程并发编程；
			- 使用 MySQL 与 Redis 进行数据存储与缓存设计；
			- 基于 Kafka 实现异步消息解耦；运用 Docker 与 Kubernetes 完成容器化部署；熟悉 Linux 运维。
			任职要求：5 年以上 Java 开发经验，深入理解 Spring Framework、MyBatis、JPA；
			熟悉分布式系统设计；了解设计模式与数据结构、算法；熟悉 TCP/IP、HTTP 网络协议，熟悉 Netty 者优先。""";

	private TestFixtures() { }

	/** 生成符合 OpenAPI 约束（minLength 8）的幂等键。 */
	public static String newKey() {
		return UUID.randomUUID().toString();
	}

	/** 构造创建岗位请求体（companyName/title 必填，jdRawText 用样例）。 */
	public static String createJobBody(String companyName, String title) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("companyName", companyName);
		body.put("title", title);
		body.put("jdRawText", SAMPLE_JD);
		return writeJson(body);
	}

	/** 构造创建岗位请求体，允许指定自定义 jdRawText（AT-03 用不同 JD 触发回退）。 */
	public static String createJobBody(String companyName, String title, String jdRawText) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("companyName", companyName);
		body.put("title", title);
		body.put("jdRawText", jdRawText);
		return writeJson(body);
	}

	/** 构造更新投递决定请求体（decisionStatus + reason）。 */
	public static String updateDecisionBody(String decisionStatus, String reason) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("decisionStatus", decisionStatus);
		if (reason != null) {
			body.put("decisionReason", reason);
		}
		return writeJson(body);
	}

	/**
	 * 构造更新 JD 请求体（回填 companyName/title + 新 jdRawText）。
	 * JobService.updateJob 的 updateBasicInfo 全字段覆盖写，若只发 jdRawText 会把 NOT NULL 的
	 * company_name/title 清空触发约束失败，故必须回填基础字段。
	 */
	public static String updateJdBody(String companyName, String title, String jdRawText) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("companyName", companyName);
		body.put("title", title);
		body.put("jdRawText", jdRawText);
		return writeJson(body);
	}

	/** 构造更新要求请求体（confirmationStatus 必填 + 可选 manualMatchStatus/reason）。 */
	public static String updateRequirementBody(String confirmationStatus, String manualMatchStatus, String reason) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("confirmationStatus", confirmationStatus);
		if (manualMatchStatus != null) {
			body.put("manualMatchStatus", manualMatchStatus);
		}
		if (reason != null) {
			body.put("reason", reason);
		}
		return writeJson(body);
	}

	/**
	 * 构造带 JSON content-type 的请求实体，附加任意头（key,value 成对）。
	 * 例：httpWithHeaders(body, "Idempotency-Key", k1, "If-Match-Version", "0")
	 */
	public static HttpEntity<String> httpWithHeaders(String body, String... headers) {
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		for (int i = 0; i + 1 < headers.length; i += 2) {
			h.add(headers[i], headers[i + 1]);
		}
		return new HttpEntity<>(body, h);
	}

	/** 构造带 JSON content-type 但无额外头的请求实体。 */
	public static HttpEntity<String> httpJson(String body) {
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(body, h);
	}

	private static String writeJson(Map<String, Object> body) {
		try {
			return MAPPER.writeValueAsString(body);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
