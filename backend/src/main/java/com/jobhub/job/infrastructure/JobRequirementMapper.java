package com.jobhub.job.infrastructure;

import com.jobhub.job.domain.ConfirmationStatus;
import com.jobhub.job.domain.JobRequirement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

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

	@Select("SELECT * FROM job_requirement WHERE job_id = #{jobId} AND deleted_at IS NULL ORDER BY sort_order ASC")
	List<JobRequirement> selectByJobId(@Param("jobId") String jobId);

	@Select("SELECT * FROM job_requirement WHERE job_id = #{jobId} AND confirmation_status = 'CONFIRMED' " +
			"AND deleted_at IS NULL ORDER BY sort_order ASC")
	List<JobRequirement> selectConfirmedByJobId(@Param("jobId") String jobId);

	@Select("SELECT * FROM job_requirement WHERE id = #{id} AND deleted_at IS NULL")
	JobRequirement selectById(@Param("id") String id);

	@Update("UPDATE job_requirement SET normalized_name=#{normalizedName, jdbcType=VARCHAR}, " +
			"requirement_type=#{type}, proficiency_text=#{proficiencyText, jdbcType=VARCHAR}, " +
			"confirmation_status=#{confirmationStatus}, updated_at=#{updatedAt} " +
			"WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateByIdAndVersion(JobRequirement r, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE job_requirement SET confirmation_status=#{confirmationStatus}, updated_at=#{updatedAt} " +
			"WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateStatusByIdAndVersion(JobRequirement r, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE job_requirement SET confirmation_status='PENDING', updated_at=#{now} " +
			"WHERE job_id=#{jobId} AND deleted_at IS NULL")
	int markAllPendingByJobId(@Param("jobId") String jobId, @Param("now") String now);

	@Update("UPDATE job_requirement SET version = version + 1 " +
			"WHERE id = #{id} AND version = #{expectedVersion} AND deleted_at IS NULL")
	int bumpVersionByIdAndVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);
}
