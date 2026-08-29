package com.jobhub.skill.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.VersionConflictException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.skill.domain.SkillProfile;
import com.jobhub.skill.infrastructure.SkillProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 技能画像。P0 仅支持显式修改自评等级：无自评记录的技能首次设置时创建 user_skill；
 * evidence_status 与 interview_performance 为独立维度，本服务不修改。
 */
@Service
public class SkillProfileService {
	private static final String SINGLE_USER_ID = "00000000-0000-0000-0000-000000000001";

	private final SkillProfileMapper skillProfileMapper;
	private final IdGenerator ids;
	private final UtcTime time;

	public SkillProfileService(SkillProfileMapper skillProfileMapper, IdGenerator ids, UtcTime time) {
		this.skillProfileMapper = skillProfileMapper;
		this.ids = ids;
		this.time = time;
	}

	public List<SkillProfile> list() {
		return skillProfileMapper.selectAll();
	}

	@Transactional
	public SkillProfile updateSelfLevel(String skillId, long expectedVersion, int selfLevel) {
		SkillProfile profile = requireSkillProfile(skillId);
		String now = time.now();
		if (profile.getUserSkillId() == null) {
			// 首次自评：创建 user_skill；若唯一键冲突说明自评记录已被并发创建，按版本冲突处理
			if (skillProfileMapper.insertIfAbsent(ids.newId(), SINGLE_USER_ID, skillId, selfLevel, now) == 0) {
				throw new VersionConflictException(requireSkillProfile(skillId).getVersion());
			}
			return requireSkillProfile(skillId);
		}
		VersionCheck.requireAffected(
			skillProfileMapper.updateSelfLevel(profile.getUserSkillId(), selfLevel, expectedVersion, now),
			profile.getVersion());
		return requireSkillProfile(skillId);
	}

	private SkillProfile requireSkillProfile(String skillId) {
		SkillProfile profile = skillProfileMapper.selectBySkillId(skillId);
		VersionCheck.requireFound(profile, "SkillProfile", skillId);
		return profile;
	}
}
