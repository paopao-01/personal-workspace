package com.jobhub.skill.api;

import com.jobhub.skill.domain.SkillProfile;

public record SkillProfileResponse(
	String skillId,
	String skillName,
	Integer selfLevel,
	String evidenceStatus,
	Long version
) {
	public static SkillProfileResponse from(SkillProfile profile) {
		return new SkillProfileResponse(
			profile.getSkillId(),
			profile.getSkillName(),
			profile.getSelfLevel(),
			profile.getEvidenceStatus(),
			profile.getVersion()
		);
	}
}
