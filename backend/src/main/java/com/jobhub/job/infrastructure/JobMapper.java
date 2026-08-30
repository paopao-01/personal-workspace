package com.jobhub.job.infrastructure;

import com.jobhub.job.domain.Job;
import com.jobhub.job.domain.JobDecisionStatus;
import com.jobhub.job.domain.JobStatus;
import com.jobhub.job.application.JobListMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

@Mapper
public interface JobMapper {

	@Insert("INSERT INTO job_posting (id, company_name, title, jd_raw_text, source, source_url, " +
			"location, salary_range, decision_status, decision_reason, status, notes, " +
			"created_at, updated_at, version) VALUES (" +
			"#{id}, #{companyName}, #{title}, #{jdRawText}, #{source, jdbcType=VARCHAR}, #{sourceUrl, jdbcType=VARCHAR}, " +
			"#{location, jdbcType=VARCHAR}, #{salaryRange, jdbcType=VARCHAR}, " +
			"#{decisionStatus, jdbcType=VARCHAR}, #{decisionReason, jdbcType=VARCHAR}, " +
			"#{status}, #{notes, jdbcType=VARCHAR}, " +
			"#{createdAt}, #{updatedAt}, #{version})")
	int insert(Job job);

	@Select("SELECT * FROM job_posting WHERE id = #{id} AND deleted_at IS NULL")
	Job selectById(@Param("id") String id);

	@Select("<script>" +
			"SELECT * FROM job_posting WHERE deleted_at IS NULL " +
			"<if test='query != null and query != \"\"'>" +
			"  AND (company_name LIKE '%' || #{query} || '%' OR title LIKE '%' || #{query} || '%')" +
			"</if>" +
			"<if test='decisionStatus != null'>" +
			"  AND decision_status = #{decisionStatus}" +
			"</if>" +
			"<if test='jobStatus != null'>" +
			"  AND status = #{jobStatus}" +
			"</if>" +
			"<if test='location != null and location != \"\"'>AND lower(location) LIKE '%' || lower(#{location}) || '%'</if>" +
			"<if test='source != null and source != \"\"'>AND lower(source) LIKE '%' || lower(#{source}) || '%'</if>" +
			"<if test='hasPendingRequirements != null'>AND (EXISTS (SELECT 1 FROM job_requirement r WHERE r.job_id=job_posting.id AND r.deleted_at IS NULL AND r.confirmation_status='PENDING')) = #{hasPendingRequirements}</if>" +
			"ORDER BY updated_at DESC LIMIT #{pageSize} OFFSET #{offset}" +
			"</script>")
	List<Job> selectPage(@Param("query") String query,
						 @Param("decisionStatus") JobDecisionStatus decisionStatus,
						 @Param("jobStatus") JobStatus jobStatus,
						 @Param("location") String location, @Param("source") String source,
						 @Param("hasPendingRequirements") Boolean hasPendingRequirements,
						 @Param("pageSize") int pageSize,
						 @Param("offset") int offset);

	@Select("<script>" +
			"SELECT COUNT(*) FROM job_posting WHERE deleted_at IS NULL " +
			"<if test='query != null and query != \"\"'>" +
			"  AND (company_name LIKE '%' || #{query} || '%' OR title LIKE '%' || #{query} || '%')" +
			"</if>" +
			"<if test='decisionStatus != null'>" +
			"  AND decision_status = #{decisionStatus}" +
			"</if>" +
			"<if test='jobStatus != null'>" +
			"  AND status = #{jobStatus}" +
			"</if>" +
			"<if test='location != null and location != \"\"'>AND lower(location) LIKE '%' || lower(#{location}) || '%'</if>" +
			"<if test='source != null and source != \"\"'>AND lower(source) LIKE '%' || lower(#{source}) || '%'</if>" +
			"<if test='hasPendingRequirements != null'>AND (EXISTS (SELECT 1 FROM job_requirement r WHERE r.job_id=job_posting.id AND r.deleted_at IS NULL AND r.confirmation_status='PENDING')) = #{hasPendingRequirements}</if>" +
			"</script>")
	long selectPageCount(@Param("query") String query,
						@Param("decisionStatus") JobDecisionStatus decisionStatus,
						@Param("jobStatus") JobStatus jobStatus, @Param("location") String location,
						@Param("source") String source, @Param("hasPendingRequirements") Boolean hasPendingRequirements);

	@Select("<script>SELECT j.id AS jobId, " +
			"SUM(CASE WHEN r.confirmation_status='CONFIRMED' THEN 1 ELSE 0 END) AS confirmedRequirementCount, " +
			"SUM(CASE WHEN r.confirmation_status='PENDING' THEN 1 ELSE 0 END) AS pendingRequirementCount, " +
			"SUM(CASE WHEN m.invalidated_at IS NULL AND m.match_status='NOT_MET' THEN 1 ELSE 0 END) AS notMetCount, " +
			"SUM(CASE WHEN m.invalidated_at IS NULL AND m.match_status='INSUFFICIENT_INFO' THEN 1 ELSE 0 END) AS insufficientInfoCount, " +
			"EXISTS (SELECT 1 FROM application_record a WHERE a.job_id=j.id AND a.deleted_at IS NULL AND a.status IN ('DRAFT','APPLIED','RESUME_PASSED','INTERVIEWING','ON_HOLD')) AS hasActiveApplication " +
			"FROM job_posting j LEFT JOIN job_requirement r ON r.job_id=j.id AND r.deleted_at IS NULL " +
			"LEFT JOIN requirement_match m ON m.requirement_id=r.id WHERE j.id IN " +
			"<foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach> GROUP BY j.id</script>")
	List<JobListMeta> selectListMetaByIds(@Param("ids") List<String> ids);

	@Update("UPDATE job_posting SET company_name=#{job.companyName}, title=#{job.title}, jd_raw_text=#{job.jdRawText}, " +
			"source=#{job.source, jdbcType=VARCHAR}, source_url=#{job.sourceUrl, jdbcType=VARCHAR}, " +
			"location=#{job.location, jdbcType=VARCHAR}, salary_range=#{job.salaryRange, jdbcType=VARCHAR}, " +
			"notes=#{job.notes, jdbcType=VARCHAR}, updated_at=#{job.updatedAt} " +
			"WHERE id=#{job.id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateBasicInfoByIdAndVersion(@Param("job") Job job, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE job_posting SET decision_status=#{job.decisionStatus, jdbcType=VARCHAR}, " +
			"decision_reason=#{job.decisionReason, jdbcType=VARCHAR}, updated_at=#{job.updatedAt} " +
			"WHERE id=#{job.id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateDecisionByIdAndVersion(@Param("job") Job job, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE job_posting SET status=#{job.status}, updated_at=#{job.updatedAt} " +
			"WHERE id=#{job.id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateStatusByIdAndVersion(@Param("job") Job job, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE job_posting SET version = version + 1 " +
			"WHERE id = #{id} AND version = #{expectedVersion} AND deleted_at IS NULL")
	int bumpVersionByIdAndVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);

	/** 按 id 批量查询未删除岗位（dashboard 聚合避免 N+1）。空列表返回空结果。 */
	@Select("<script>" +
			"SELECT * FROM job_posting WHERE deleted_at IS NULL AND id IN " +
			"<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
			"</script>")
	List<Job> selectByIds(@Param("ids") List<String> ids);
}
