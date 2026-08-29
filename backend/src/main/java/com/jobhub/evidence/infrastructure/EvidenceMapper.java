package com.jobhub.evidence.infrastructure;

import com.jobhub.evidence.domain.Evidence;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface EvidenceMapper {
	@Insert("""
		INSERT INTO evidence (
		  id, type, title, where_used, problem_solved, approach, result_text, url_or_path,
		  created_at, updated_at, version
		) VALUES (
		  #{id}, #{type}, #{title}, #{whereUsed}, #{problemSolved}, #{approach}, #{resultText}, #{urlOrPath},
		  #{createdAt}, #{updatedAt}, #{version}
		)
		""")
	int insert(Evidence evidence);

	@Select("""
		SELECT id, type, title, where_used, problem_solved, approach, result_text, url_or_path,
		       created_at, updated_at, deleted_at, version
		FROM evidence
		WHERE deleted_at IS NULL
		ORDER BY updated_at DESC, title
		""")
	List<Evidence> selectAll();

	@Select("""
		SELECT id, type, title, where_used, problem_solved, approach, result_text, url_or_path,
		       created_at, updated_at, deleted_at, version
		FROM evidence
		WHERE id=#{id} AND deleted_at IS NULL
		""")
	Evidence selectById(@Param("id") String id);

	@Select("""
		SELECT id, type, title, where_used, problem_solved, approach, result_text, url_or_path,
		       created_at, updated_at, deleted_at, version
		FROM evidence
		WHERE id=#{id}
		""")
	Evidence selectByIdIncludeTrashed(@Param("id") String id);

	@Update("""
		UPDATE evidence
		SET type=#{evidence.type},
		    title=#{evidence.title},
		    where_used=#{evidence.whereUsed},
		    problem_solved=#{evidence.problemSolved},
		    approach=#{evidence.approach},
		    result_text=#{evidence.resultText},
		    url_or_path=#{evidence.urlOrPath},
		    updated_at=#{evidence.updatedAt}
		WHERE id=#{evidence.id}
		  AND version=#{expectedVersion}
		  AND deleted_at IS NULL
		""")
	int updateMeta(@Param("evidence") Evidence evidence, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE evidence SET version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int bumpVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);

	@Select("""
		SELECT skill_id
		FROM skill_evidence
		WHERE evidence_id=#{evidenceId}
		ORDER BY skill_id
		""")
	List<String> selectSkillIds(@Param("evidenceId") String evidenceId);

	@Delete("DELETE FROM skill_evidence WHERE evidence_id=#{evidenceId}")
	int deleteSkillRefs(@Param("evidenceId") String evidenceId);

	@Insert("""
		INSERT OR IGNORE INTO skill_evidence (skill_id, evidence_id, created_at)
		VALUES (#{skillId}, #{evidenceId}, #{now})
		""")
	int insertSkillRef(@Param("skillId") String skillId, @Param("evidenceId") String evidenceId,
			@Param("now") String now);

	@Select("""
		SELECT COUNT(*)
		FROM skill
		WHERE id=#{skillId} AND deleted_at IS NULL
		""")
	long countActiveSkill(@Param("skillId") String skillId);

	@Select("SELECT COUNT(*) FROM project_evidence WHERE evidence_id=#{evidenceId}")
	long countProjectRefs(@Param("evidenceId") String evidenceId);

	@Select("SELECT COUNT(*) FROM skill_evidence WHERE evidence_id=#{evidenceId}")
	long countSkillRefs(@Param("evidenceId") String evidenceId);

	@Update("""
		UPDATE evidence
		SET deleted_at=#{now}, updated_at=#{now}, version=version+1
		WHERE id=#{id}
		  AND version=#{expectedVersion}
		  AND deleted_at IS NULL
		""")
	int softDelete(@Param("id") String id, @Param("expectedVersion") long expectedVersion, @Param("now") String now);

	@Update("UPDATE evidence SET deleted_at=NULL, updated_at=#{now} WHERE id=#{id} AND deleted_at IS NOT NULL")
	int restoreById(@Param("id") String id, @Param("now") String now);

	@Delete("DELETE FROM evidence WHERE id=#{id}")
	int hardDelete(@Param("id") String id);

	@Delete("DELETE FROM project_evidence WHERE evidence_id=#{evidenceId}")
	int deleteProjectRefs(@Param("evidenceId") String evidenceId);
}
