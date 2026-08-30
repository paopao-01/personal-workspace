package com.jobhub.job.infrastructure;

import com.jobhub.job.domain.ConfirmationStatus;
import com.jobhub.job.domain.JobRequirement;
import com.jobhub.job.application.RequirementSkillFact;
import com.jobhub.job.application.GapEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface JobRequirementMapper {

	@Insert("<script>" +
			"INSERT INTO job_requirement (id, job_id, raw_text, normalized_name, requirement_type, " +
			"proficiency_text, confirmation_status, source_type, sort_order, version, created_at, updated_at) VALUES " +
			"<foreach collection='items' item='r' separator=','>" +
			"(#{r.id}, #{r.jobId}, #{r.rawText}, #{r.normalizedName, jdbcType=VARCHAR}, " +
			"#{r.type}, #{r.proficiencyText, jdbcType=VARCHAR}, " +
			"#{r.confirmationStatus}, #{r.source}, #{r.sortOrder}, #{r.version}, #{r.createdAt}, #{r.updatedAt})" +
			"</foreach>" +
			"</script>")
	int batchInsert(@Param("items") List<JobRequirement> items);

	@Select("SELECT id, job_id, raw_text, normalized_name, requirement_type AS \"type\", " +
			"proficiency_text, confirmation_status, source_type AS \"source\", sort_order, " +
			"merged_into_requirement_id, version, created_at, updated_at, deleted_at " +
			"FROM job_requirement WHERE job_id = #{jobId} AND deleted_at IS NULL ORDER BY sort_order ASC")
	List<JobRequirement> selectByJobId(@Param("jobId") String jobId);

	@Select("SELECT id, job_id, raw_text, normalized_name, requirement_type AS \"type\", " +
			"proficiency_text, confirmation_status, source_type AS \"source\", sort_order, " +
			"merged_into_requirement_id, version, created_at, updated_at, deleted_at " +
			"FROM job_requirement WHERE job_id = #{jobId} AND confirmation_status = 'CONFIRMED' " +
			"AND deleted_at IS NULL ORDER BY sort_order ASC")
	List<JobRequirement> selectConfirmedByJobId(@Param("jobId") String jobId);

	@Select("SELECT id, job_id, raw_text, normalized_name, requirement_type AS \"type\", " +
			"proficiency_text, confirmation_status, source_type AS \"source\", sort_order, " +
			"merged_into_requirement_id, version, created_at, updated_at, deleted_at " +
			"FROM job_requirement WHERE id = #{id} AND deleted_at IS NULL")
	JobRequirement selectById(@Param("id") String id);

	@Update("UPDATE job_requirement SET raw_text=#{r.rawText}, normalized_name=#{r.normalizedName, jdbcType=VARCHAR}, " +
			"requirement_type=#{r.type}, proficiency_text=#{r.proficiencyText, jdbcType=VARCHAR}, " +
			"confirmation_status=#{r.confirmationStatus}, updated_at=#{r.updatedAt} " +
			"WHERE id=#{r.id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateByIdAndVersion(@Param("r") JobRequirement r, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE job_requirement SET confirmation_status=#{r.confirmationStatus}, updated_at=#{r.updatedAt} " +
			"WHERE id=#{r.id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateStatusByIdAndVersion(@Param("r") JobRequirement r, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE job_requirement SET confirmation_status='PENDING', updated_at=#{now} " +
			"WHERE job_id=#{jobId} AND deleted_at IS NULL")
	int markAllPendingByJobId(@Param("jobId") String jobId, @Param("now") String now);

	@Update("UPDATE job_requirement SET version = version + 1 " +
			"WHERE id = #{id} AND version = #{expectedVersion} AND deleted_at IS NULL")
	int bumpVersionByIdAndVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE job_requirement SET deleted_at = #{now}, updated_at = #{now}, " +
			"merged_into_requirement_id = #{targetId} WHERE id = #{id} AND deleted_at IS NULL")
	int mergeInto(@Param("id") String id, @Param("targetId") String targetId, @Param("now") String now);

	@Update("UPDATE job_requirement SET deleted_at=#{now}, updated_at=#{now}, version=version+1 " +
			"WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int softDelete(@Param("id") String id, @Param("expectedVersion") long expectedVersion, @Param("now") String now);

	@Delete("DELETE FROM requirement_skill WHERE requirement_id=#{requirementId}")
	int deleteSkillRefs(@Param("requirementId") String requirementId);

	@Insert("INSERT OR IGNORE INTO requirement_skill (requirement_id, skill_id, created_at) " +
			"VALUES (#{requirementId}, #{skillId}, #{now})")
	int insertSkillRef(@Param("requirementId") String requirementId, @Param("skillId") String skillId,
					   @Param("now") String now);

	@Select("SELECT rs.skill_id AS skillId, us.self_level AS selfLevel, " +
			"(SELECT COUNT(*) FROM skill_evidence se JOIN evidence e ON e.id=se.evidence_id AND e.deleted_at IS NULL WHERE se.skill_id=rs.skill_id) AS evidenceCount " +
			"FROM requirement_skill rs LEFT JOIN user_skill us ON us.skill_id=rs.skill_id " +
			"AND us.user_id='00000000-0000-0000-0000-000000000001' WHERE rs.requirement_id=#{requirementId}")
	List<RequirementSkillFact> selectSkillFacts(@Param("requirementId") String requirementId);

	@Select("SELECT e.id, e.type, e.title, e.url_or_path AS urlOrPath FROM requirement_skill rs " +
			"JOIN skill_evidence se ON se.skill_id=rs.skill_id JOIN evidence e ON e.id=se.evidence_id " +
			"WHERE rs.requirement_id=#{requirementId} AND e.deleted_at IS NULL ORDER BY e.title")
	List<GapEvidence> selectActiveEvidence(@Param("requirementId") String requirementId);
}
