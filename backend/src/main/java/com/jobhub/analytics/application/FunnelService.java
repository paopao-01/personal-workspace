package com.jobhub.analytics.application;

import com.jobhub.analytics.api.FunnelBucket;
import com.jobhub.analytics.infrastructure.FunnelMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 投递漏斗转化时间序列只读聚合服务。
 *
 * 不写入任何数据；按 applied_at 与 granularity 分组返回每桶投递/面试/Offer 原始计数与转化率。
 * 不输出趋势结论、能力等级、归因或行动建议。
 */
@Service
public class FunnelService {

	private final FunnelMapper mapper;

	public FunnelService(FunnelMapper mapper) {
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public List<FunnelBucket> report(String from, String to, String granularity) {
		String validatedFrom = parseIsoUtc(from, "from");
		String validatedTo = parseIsoUtc(to, "to");
		if (!"day".equalsIgnoreCase(granularity) && !"hour".equalsIgnoreCase(granularity)) {
			throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "granularity 必须为 day 或 hour");
		}
		List<Map<String, Object>> rows = mapper.selectFunnel(validatedFrom, validatedTo, granularity);
		return rows.stream().map(row -> {
			String date = String.valueOf(row.get("date"));
			long total = ((Number) row.get("total")).longValue();
			long applied = ((Number) row.get("applied")).longValue();
			long interviewed = ((Number) row.get("interviewed")).longValue();
			long offered = ((Number) row.get("offered")).longValue();
			double interviewRate = applied == 0 ? 0.0 : (double) interviewed / applied;
			double offerRate = applied == 0 ? 0.0 : (double) offered / applied;
			return new FunnelBucket(date, total, applied, interviewed, offered, interviewRate, offerRate);
		}).toList();
	}

	/** 校验 from/to 为合法 ISO-8601 UTC（用 Instant.parse 校验），空返回 null（不过滤）。 */
	private static String parseIsoUtc(String value, String field) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			Instant.parse(value);
			return value;
		} catch (DateTimeParseException ex) {
			throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
					field + " 必须为合法 ISO-8601 UTC 字符串（如 2026-09-07T13:00:00Z）");
		}
	}
}
