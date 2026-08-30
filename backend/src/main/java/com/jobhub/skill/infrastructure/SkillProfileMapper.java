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
		       CASE WHEN EXISTS (SELECT 1 FROM skill_evidence se JOIN evidence e ON e.id=se.evidence_id AND e.deleted_at IS NULL WHERE se.skill_id=s.id)
		            THEN 'VALID' ELSE us.evidence_status END AS evidenceStatus,
		       us.interview_performance_json AS interviewPerformanceJson,
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
		       CASE WHEN EXISTS (SELECT 1 FROM skill_evidence se JOIN evidence e ON e.id=se.evidence_id AND e.deleted_at IS NULL WHERE se.skill_id=s.id)
		            THEN 'VALID' ELSE us.evidence_status END AS evidenceStatus,
		       us.interview_performance_json AS interviewPerformanceJson,
		       COALESCE(us.version, 0) AS version
		FROM skill s
		LEFT JOIN user_skill us ON us.skill_id = s.id
		WHERE s.id=#{skillId} AND s.deleted_at IS NULL
		""")
	SkillProfile selectBySkillId(@Param("skillId") String skillId);

	@Select("SELECT s.id FROM skill s LEFT JOIN skill_alias a ON a.skill_id=s.id " +
			"WHERE s.deleted_at IS NULL AND (s.normalized_name=lower(#{name}) OR a.normalized_alias=lower(#{name})) LIMIT 1")
	String findActiveSkillIdByNameOrAlias(@Param("name") String name);

	@Insert("INSERT INTO skill (id, name, normalized_name, category, is_system, created_at, updated_at) " +
			"VALUES (#{id}, #{name}, #{normalizedName}, #{category, jdbcType=VARCHAR}, 0, #{now}, #{now})")
	int insertSkill(@Param("id") String id, @Param("name") String name, @Param("normalizedName") String normalizedName,
				@Param("category") String category, @Param("now") String now);

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
