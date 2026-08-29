package com.jobhub.skill.infrastructure;

import com.jobhub.skill.domain.SkillProfile;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface SkillProfileMapper {
	@Select("""
		SELECT s.id AS skillId,
		       s.name AS skillName,
		       us.id AS userSkillId,
		       us.self_level AS selfLevel,
		       us.evidence_status AS evidenceStatus,
		       COALESCE(us.version, 0) AS version
		FROM skill s
		LEFT JOIN user_skill us ON us.skill_id = s.id
		WHERE s.deleted_at IS NULL
		ORDER BY s.name
		""")
	List<SkillProfile> selectAll();

	@Select("""
		SELECT s.id AS skillId,
		       s.name AS skillName,
		       us.id AS userSkillId,
		       us.self_level AS selfLevel,
		       us.evidence_status AS evidenceStatus,
		       COALESCE(us.version, 0) AS version
		FROM skill s
		LEFT JOIN user_skill us ON us.skill_id = s.id
		WHERE s.id=#{skillId} AND s.deleted_at IS NULL
		""")
	SkillProfile selectBySkillId(@Param("skillId") String skillId);

	@Update("""
		UPDATE user_skill
		SET self_level=#{selfLevel}, updated_at=#{now}, version=version+1
		WHERE id=#{userSkillId} AND version=#{expectedVersion}
		""")
	int updateSelfLevel(@Param("userSkillId") String userSkillId, @Param("selfLevel") int selfLevel,
			@Param("expectedVersion") long expectedVersion, @Param("now") String now);

	@Insert("""
		INSERT OR IGNORE INTO user_skill (id, user_id, skill_id, self_level, evidence_status, created_at, updated_at, version)
		VALUES (#{id}, #{userId}, #{skillId}, #{selfLevel}, 'NO_EVIDENCE', #{now}, #{now}, 0)
		""")
	int insertIfAbsent(@Param("id") String id, @Param("userId") String userId, @Param("skillId") String skillId,
			@Param("selfLevel") int selfLevel, @Param("now") String now);
}
