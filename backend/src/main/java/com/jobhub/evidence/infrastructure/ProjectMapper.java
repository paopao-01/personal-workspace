package com.jobhub.evidence.infrastructure;

import com.jobhub.evidence.domain.Evidence;
import com.jobhub.evidence.domain.ProjectCase;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ProjectMapper {
	@Insert("""
		INSERT INTO project (
		  id, title, scenario, approach, problem_solved, result_text, created_at, updated_at, version
		) VALUES (
		  #{id}, #{title}, #{scenario}, #{approach}, #{problemSolved}, #{resultText}, #{createdAt}, #{updatedAt}, #{version}
		)
		""")
	int insert(ProjectCase project);

	@Select("""
		SELECT id, title, scenario, approach, problem_solved, result_text,
		       created_at, updated_at, deleted_at, version
		FROM project
		WHERE deleted_at IS NULL
		ORDER BY updated_at DESC, title
		""")
	List<ProjectCase> selectAll();

	@Select("""
		SELECT id, title, scenario, approach, problem_solved, result_text,
		       created_at, updated_at, deleted_at, version
		FROM project
		WHERE id=#{id} AND deleted_at IS NULL
		""")
	ProjectCase selectById(@Param("id") String id);

	@Update("""
		UPDATE project
		SET title=#{project.title},
		    scenario=#{project.scenario},
		    approach=#{project.approach},
		    problem_solved=#{project.problemSolved},
		    result_text=#{project.resultText},
		    updated_at=#{project.updatedAt}
		WHERE id=#{project.id}
		  AND version=#{expectedVersion}
		  AND deleted_at IS NULL
		""")
	int updateMeta(@Param("project") ProjectCase project, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE project SET version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int bumpVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);

	@Select("""
		SELECT e.id, e.type, e.title, e.url_or_path, e.deleted_at
		FROM evidence e
		JOIN project_evidence pe ON pe.evidence_id = e.id
		WHERE pe.project_id = #{projectId}
		ORDER BY e.title
		""")
	List<Evidence> selectEvidenceRefs(@Param("projectId") String projectId);

	@Select("""
		SELECT COUNT(*)
		FROM project_evidence
		WHERE project_id=#{projectId}
		""")
	long countEvidenceRefs(@Param("projectId") String projectId);

	@Update("""
		UPDATE project
		SET deleted_at=#{now}, updated_at=#{now}, version=version+1
		WHERE id=#{id}
		  AND version=#{expectedVersion}
		  AND deleted_at IS NULL
		""")
	int softDelete(@Param("id") String id, @Param("expectedVersion") long expectedVersion, @Param("now") String now);

	@Update("UPDATE project SET deleted_at=NULL, updated_at=#{now} WHERE id=#{id} AND deleted_at IS NOT NULL")
	int restoreById(@Param("id") String id, @Param("now") String now);

	@Delete("DELETE FROM project WHERE id=#{id}")
	int hardDelete(@Param("id") String id);

	@Delete("DELETE FROM project_evidence WHERE project_id=#{projectId}")
	int deleteEvidenceRefs(@Param("projectId") String projectId);

	@Insert("""
		INSERT OR IGNORE INTO project_evidence (project_id, evidence_id, created_at)
		VALUES (#{projectId}, #{evidenceId}, #{now})
		""")
	int insertEvidenceRef(@Param("projectId") String projectId, @Param("evidenceId") String evidenceId,
			@Param("now") String now);
}
