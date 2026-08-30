package com.jobhub.job.infrastructure;

import com.jobhub.job.domain.RequirementMatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface RequirementMatchMapper {

	@Select("SELECT * FROM requirement_match WHERE requirement_id = #{requirementId}")
	RequirementMatch selectByRequirementId(@Param("requirementId") String requirementId);

	@Select("<script>" +
			"SELECT * FROM requirement_match WHERE invalidated_at IS NULL AND requirement_id IN " +
			"<foreach collection='ids' item='id' open='(' close=')' separator=','>" +
			"#{id}" +
			"</foreach>" +
			"</script>")
	List<RequirementMatch> selectByRequirementIds(@Param("ids") List<String> ids);

	@Insert("INSERT INTO requirement_match (id, requirement_id, match_status, evidence_snapshot_json, " +
			"manual_override_reason, calculated_at, updated_at, version) VALUES (" +
			"#{id}, #{requirementId}, #{matchStatus}, #{evidenceSnapshotJson}, " +
			"#{manualOverrideReason, jdbcType=VARCHAR}, #{calculatedAt}, #{updatedAt}, #{version})")
	int insert(RequirementMatch match);

	@Update("UPDATE requirement_match SET match_status=#{match.matchStatus}, " +
			"manual_override_reason=#{match.manualOverrideReason, jdbcType=VARCHAR}, invalidated_at=NULL, " +
			"updated_at=#{match.updatedAt}, version=version+1 " +
			"WHERE requirement_id=#{match.requirementId} AND version=#{expectedVersion}")
	int updateByRequirementIdAndVersion(@Param("match") RequirementMatch match, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE requirement_match SET invalidated_at=#{now}, updated_at=#{now}, version=version+1 " +
			"WHERE invalidated_at IS NULL AND requirement_id IN " +
			"(SELECT id FROM job_requirement WHERE job_id = #{jobId})")
	int invalidateByJobId(@Param("jobId") String jobId, @Param("now") String now);
}
