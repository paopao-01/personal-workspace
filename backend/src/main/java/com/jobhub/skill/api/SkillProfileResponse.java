package com.jobhub.skill.api;

import com.jobhub.skill.domain.SkillProfile;
import com.fasterxml.jackson.databind.ObjectMapper;

public record SkillProfileResponse(
	String skillId,
	String skillName,
	Integer selfLevel,
	String evidenceStatus,
	Object interviewPerformance,
	Long version
) {
	private static final ObjectMapper JSON = new ObjectMapper();

	public static SkillProfileResponse from(SkillProfile profile) {
		return new SkillProfileResponse(
			profile.getSkillId(),
			profile.getSkillName(),
			profile.getSelfLevel(),
			profile.getEvidenceStatus(),
			parse(profile.getInterviewPerformanceJson()),
			profile.getVersion()
		);
	}

	private static Object parse(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return JSON.readValue(value, Object.class);
		} catch (Exception ignored) {
			return null;
		}
	}
}
