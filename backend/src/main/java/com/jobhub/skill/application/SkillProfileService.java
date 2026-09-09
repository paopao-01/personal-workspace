package com.jobhub.skill.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.VersionConflictException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.skill.domain.SelfLevelHistoryEntry;
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
	public SkillProfile create(String name, String category) {
		String displayName = required(name, "Skill name is required");
		String normalizedName = displayName.toLowerCase(java.util.Locale.ROOT);
		if (skillProfileMapper.findActiveSkillIdByNameOrAlias(normalizedName) != null) {
			throw new BusinessRuleException("A skill with the same name or alias already exists");
		}
		String id = ids.newId();
		skillProfileMapper.insertSkill(id, displayName, normalizedName,
				category == null || category.isBlank() ? null : category.trim(), time.now());
		return requireSkillProfile(id);
	}

	@Transactional
	public SkillProfile updateSelfLevel(String skillId, long expectedVersion, int selfLevel, String reason, String idempotencyKey) {
		SkillProfile profile = requireSkillProfile(skillId);
		String now = time.now();
		if (profile.getUserSkillId() == null) {
			// 首次自评：创建 user_skill；若唯一键冲突说明自评记录已被并发创建，按版本冲突处理
			if (skillProfileMapper.insertIfAbsent(ids.newId(), SINGLE_USER_ID, skillId, selfLevel, now) == 0) {
				throw new VersionConflictException(requireSkillProfile(skillId).getVersion());
			}
			// 追加写历史：首次自评 fromLevel=null（无前值）
			skillProfileMapper.insertHistory(ids.newId(), requireSkillProfile(skillId).getUserSkillId(),
					null, selfLevel, reason, idempotencyKey, now);
			return requireSkillProfile(skillId);
		}
		Integer oldLevel = profile.getSelfLevel();
		VersionCheck.requireAffected(
			skillProfileMapper.updateSelfLevel(profile.getUserSkillId(), selfLevel, expectedVersion, now),
			profile.getVersion());
		// 追加写历史：fromLevel=旧值，toLevel=新值
		skillProfileMapper.insertHistory(ids.newId(), profile.getUserSkillId(),
				oldLevel, selfLevel, reason, idempotencyKey, now);
		return requireSkillProfile(skillId);
	}

	public List<SelfLevelHistoryEntry> listSelfLevelHistory(String skillId) {
		requireSkillProfile(skillId);
		return skillProfileMapper.selectHistoryBySkillId(skillId);
	}

	private SkillProfile requireSkillProfile(String skillId) {
		SkillProfile profile = skillProfileMapper.selectBySkillId(skillId);
		VersionCheck.requireFound(profile, "SkillProfile", skillId);
		return profile;
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) throw new BusinessRuleException(message);
		return value.trim();
	}
}
