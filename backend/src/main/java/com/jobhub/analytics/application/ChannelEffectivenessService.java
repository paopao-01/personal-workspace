package com.jobhub.analytics.application;

import com.jobhub.analytics.domain.ChannelEffectiveness;
import com.jobhub.analytics.domain.ChannelEffectiveness.Group;
import com.jobhub.analytics.infrastructure.ChannelEffectivenessMapper;
import com.jobhub.analytics.infrastructure.EffectivenessRow;
import com.jobhub.common.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 投递渠道与简历版本效果对比只读聚合服务。
 *
 * 不写入任何数据；offerRate 在 applicationCount < 2 时为 null（信息不足）。
 * 不输出趋势结论、能力等级、归因或行动建议。
 */
@Service
public class ChannelEffectivenessService {

	private final ChannelEffectivenessMapper mapper;

	public ChannelEffectivenessService(ChannelEffectivenessMapper mapper) {
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public ChannelEffectiveness report(String from, String to) {
		String normalizedFrom = blankToNull(from);
		String normalizedTo = blankToNull(to);
		validateRange(normalizedFrom, normalizedTo);
		List<Group> channelGroups = mapper.selectByChannel(normalizedFrom, normalizedTo).stream()
			.map(ChannelEffectivenessService::toGroup)
			.toList();
		List<Group> resumeVersionGroups = mapper.selectByResumeVersion(normalizedFrom, normalizedTo).stream()
			.map(ChannelEffectivenessService::toGroup)
			.toList();
		return new ChannelEffectiveness(normalizedFrom, normalizedTo, channelGroups, resumeVersionGroups);
	}

	private static Group toGroup(EffectivenessRow row) {
		long applicationCount = row.getApplicationCount();
		Double offerRate = applicationCount < 2 ? null : (double) row.getOfferCount() / applicationCount;
		return new Group(row.getDimension(), applicationCount, row.getInterviewCount(), row.getOfferCount(),
				offerRate);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void validateRange(String from, String to) {
		if (from == null || to == null) {
			return;
		}
		if (from.compareTo(to) > 0) {
			throw new BusinessRuleException("起始日期必须早于或等于结束日期");
		}
	}
}
