package com.jobhub.job.application;

import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.job.domain.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 规则提取器（P0 不使用 AI）。按关键词词典扫描 JD 原文，生成候选项。
 * 结果均 PENDING，不覆盖已 CONFIRMED/IGNORED 的要求。
 *
 * 02-state-machines.md 2.2：JD 修改后候选要求置 PENDING，本类只在 extract 接口被调用时新增候选项，
 * 不主动修改既有要求的 confirmation_status。
 */
@Component
public class RequirementExtractor {

	private static final List<SkillKeyword> DICTIONARY = List.of(
			new SkillKeyword("Java", "java", RequirementType.MUST, List.of("Java", "JDK")),
			new SkillKeyword("Spring Boot", "spring boot", RequirementType.MUST, List.of("Spring Boot", "SpringBoot", "spring-boot")),
			new SkillKeyword("Spring", "spring", RequirementType.MUST, List.of("Spring Framework", "Spring MVC", "Spring AOP")),
			new SkillKeyword("MyBatis", "mybatis", RequirementType.MUST, List.of("MyBatis", "MyBatis-Plus", "mybatis-plus")),
			new SkillKeyword("JPA", "jpa", RequirementType.MUST, List.of("JPA", "Hibernate", "Hibernate JPA")),
			new SkillKeyword("MySQL", "mysql", RequirementType.MUST, List.of("MySQL", "MariaDB")),
			new SkillKeyword("Redis", "redis", RequirementType.MUST, List.of("Redis", "Redisson")),
			new SkillKeyword("Kafka", "kafka", RequirementType.MUST, List.of("Kafka", "Apache Kafka")),
			new SkillKeyword("RabbitMQ", "rabbitmq", RequirementType.MUST, List.of("RabbitMQ", "rabbit-mq")),
			new SkillKeyword("JVM", "jvm", RequirementType.MUST, List.of("JVM", "Java虚拟机")),
			new SkillKeyword("并发编程", "并发", RequirementType.MUST, List.of("并发", "多线程", "高并发", "线程池", "JUC", "Concurrent")),
			new SkillKeyword("分布式", "分布式", RequirementType.MUST, List.of("分布式", "分布式锁", "分布式事务", "分布式缓存")),
			new SkillKeyword("微服务", "微服务", RequirementType.MUST, List.of("微服务", "Microservice", "Spring Cloud", "Dubbo")),
			new SkillKeyword("Docker", "docker", RequirementType.MUST, List.of("Docker", "容器化", "Container")),
			new SkillKeyword("Kubernetes", "kubernetes", RequirementType.MUST, List.of("Kubernetes", "K8s")),
			new SkillKeyword("Linux", "linux", RequirementType.MUST, List.of("Linux", "Shell")),
			new SkillKeyword("设计模式", "设计模式", RequirementType.MUST, List.of("设计模式", "Design Pattern")),
			new SkillKeyword("数据结构", "数据结构", RequirementType.MUST, List.of("数据结构", "算法", "Algorithm")),
			new SkillKeyword("网络", "网络", RequirementType.MUST, List.of("TCP/IP", "HTTP", "网络", "Netty", "NIO")),
			new SkillKeyword("操作系统", "操作系统", RequirementType.MUST, List.of("操作系统", "OS"))
	);

	private static final Pattern BONUS_HINT = Pattern.compile("优先|加分|prefer|bonus|plus|nice to have|nice-to-have", Pattern.CASE_INSENSITIVE);
	private static final Pattern RESPONSIBILITY_HINT = Pattern.compile("负责|职责|responsib|duty|duties|will do|你将", Pattern.CASE_INSENSITIVE);
	private static final Pattern EXPERIENCE_HINT = Pattern.compile("(\\d+)\\s*年|experience", Pattern.CASE_INSENSITIVE);

	private final IdGenerator idGenerator;
	private final UtcTime utcTime;

	public RequirementExtractor(IdGenerator idGenerator, UtcTime utcTime) {
		this.idGenerator = idGenerator;
		this.utcTime = utcTime;
	}

	public List<JobRequirement> extract(String jobId, List<JobRequirement> existing) {
		// jobId-based overload is deprecated; service should call extract(jobId, jdRawText, existing).
		throw new UnsupportedOperationException("Use extract(jobId, jdRawText, existing)");
	}

	/**
	 * 主提取方法。返回新候选项（PENDING，source=RULE）。
	 * 不覆盖已存在的 normalized_name（无论其 confirmation_status）。
	 */
	public List<JobRequirement> extract(String jobId, String jdRawText, List<JobRequirement> existing) {
		List<JobRequirement> candidates = new ArrayList<>();
		if (jdRawText == null || jdRawText.isBlank()) {
			return candidates;
		}
		Set<String> existingNormalized = new HashSet<>();
		if (existing != null) {
			for (JobRequirement r : existing) {
				if (r.getNormalizedName() != null) {
					existingNormalized.add(r.getNormalizedName().toLowerCase());
				}
			}
		}

		String now = utcTime.now();
		int sortOrder = (existing == null ? 0 : existing.size());
		Set<String> added = new HashSet<>();

		for (SkillKeyword kw : DICTIONARY) {
			for (String alias : kw.aliases()) {
				int idx = jdRawText.indexOf(alias);
				if (idx >= 0) {
					String normalized = kw.normalized();
					if (existingNormalized.contains(normalized) || added.contains(normalized)) {
						continue;
					}
					int start = Math.max(0, idx - 20);
					int end = Math.min(jdRawText.length(), idx + alias.length() + 30);
					String rawText = jdRawText.substring(start, end).replaceAll("\\s+", " ").trim();
					RequirementType type = inferType(jdRawText, idx, kw);
					JobRequirement req = JobRequirement.createFromRule(
							idGenerator.newId(), jobId, rawText, kw.displayName(), type, null, sortOrder, now);
					candidates.add(req);
					added.add(normalized);
					sortOrder++;
					break;
				}
			}
		}
		return candidates;
	}

	private RequirementType inferType(String jd, int matchIdx, SkillKeyword kw) {
		// 看关键词前后 60 字符是否出现"优先/加分"或"负责/职责"
		int start = Math.max(0, matchIdx - 60);
		int end = Math.min(jd.length(), matchIdx + 60);
		String window = jd.substring(start, end);
		if (BONUS_HINT.matcher(window).find()) {
			return RequirementType.BONUS;
		}
		if (RESPONSIBILITY_HINT.matcher(window).find()) {
			return RequirementType.RESPONSIBILITY;
		}
		if (EXPERIENCE_HINT.matcher(window).find()) {
			return RequirementType.EXPERIENCE;
		}
		return kw.defaultType();
	}

	record SkillKeyword(String displayName, String normalized, RequirementType defaultType, List<String> aliases) { }
}
