package com.jobhub.application.infrastructure;

import com.jobhub.application.domain.Application;
import com.jobhub.application.domain.ApplicationStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 投递聚合根 Mapper。注解 SQL，无 XML。
 *
 * 乐观锁三步走（与 JobMapper 一致）：
 *   1. updateXxxByIdAndVersion(entity, expectedVersion) WHERE id AND version AND deleted_at IS NULL → affected
 *   2. bumpVersionByIdAndVersion(id, expectedVersion) → version+1
 *   3. VersionCheck.requireAffected(affected, currentVersion)
 *
 * 列映射依赖 mybatis map-underscore-to-camel-case（previous_active_status→previousActiveStatus 等）。
 */
@Mapper
public interface ApplicationMapper {

	@Insert("INSERT INTO application_record (id, job_id, applied_at, channel, status, previous_active_status, " +
			"resume_version, expected_salary, contact, next_action, next_action_due_at, rejection_reason, " +
			"notes, created_at, updated_at, version) VALUES (" +
			"#{id}, #{jobId}, #{appliedAt}, #{channel}, #{status}, " +
			"#{previousActiveStatus, jdbcType=VARCHAR}, " +
			"#{resumeVersion, jdbcType=VARCHAR}, #{expectedSalary, jdbcType=VARCHAR}, " +
			"#{contact, jdbcType=VARCHAR}, #{nextAction, jdbcType=VARCHAR}, " +
			"#{nextActionDueAt, jdbcType=VARCHAR}, #{rejectionReason, jdbcType=VARCHAR}, " +
			"#{notes, jdbcType=VARCHAR}, #{createdAt}, #{updatedAt}, #{version})")
	int insert(Application app);

	@Select("SELECT * FROM application_record WHERE id = #{id} AND deleted_at IS NULL")
	Application selectById(@Param("id") String id);

	/** 查同岗位的活动投递（应用层二次投递检测；唯一索引是兜底）。 */
	@Select("SELECT * FROM application_record WHERE job_id = #{jobId} " +
			"AND deleted_at IS NULL AND status IN ('DRAFT','APPLIED','RESUME_PASSED','INTERVIEWING','ON_HOLD')")
	Application selectActiveByJobId(@Param("jobId") String jobId);

	@Select("<script>" +
			"SELECT * FROM application_record WHERE deleted_at IS NULL " +
			"<if test='status != null'>AND status = #{status}</if> " +
			"<if test='overdueActionOnly != null and overdueActionOnly == true'>" +
			"  AND next_action_due_at IS NOT NULL AND next_action_due_at != '' AND next_action_due_at &lt; #{now}" +
			"</if> " +
			"ORDER BY updated_at DESC LIMIT #{pageSize} OFFSET #{offset}" +
			"</script>")
	List<Application> selectPage(@Param("status") ApplicationStatus status,
								  @Param("overdueActionOnly") Boolean overdueActionOnly,
								  @Param("now") String now,
								  @Param("pageSize") int pageSize,
								  @Param("offset") int offset);

	@Select("<script>" +
			"SELECT COUNT(*) FROM application_record WHERE deleted_at IS NULL " +
			"<if test='status != null'>AND status = #{status}</if> " +
			"<if test='overdueActionOnly != null and overdueActionOnly == true'>" +
			"  AND next_action_due_at IS NOT NULL AND next_action_due_at != '' AND next_action_due_at &lt; #{now}" +
			"</if> " +
			"</script>")
	long selectPageCount(@Param("status") ApplicationStatus status,
						 @Param("overdueActionOnly") Boolean overdueActionOnly,
						 @Param("now") String now);

	/** 更新元数据与下一步行动（不改 status/previous_active_status）。全字段覆盖写。 */
	@Update("UPDATE application_record SET channel=#{app.channel}, " +
			"resume_version=#{app.resumeVersion, jdbcType=VARCHAR}, " +
			"expected_salary=#{app.expectedSalary, jdbcType=VARCHAR}, " +
			"contact=#{app.contact, jdbcType=VARCHAR}, " +
			"next_action=#{app.nextAction, jdbcType=VARCHAR}, " +
			"next_action_due_at=#{app.nextActionDueAt, jdbcType=VARCHAR}, " +
			"rejection_reason=#{app.rejectionReason, jdbcType=VARCHAR}, " +
			"notes=#{app.notes, jdbcType=VARCHAR}, updated_at=#{app.updatedAt} " +
			"WHERE id=#{app.id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateMetaByIdAndVersion(@Param("app") Application app, @Param("expectedVersion") long expectedVersion);

	/** 更新状态与 previousActiveStatus（transition 用）。 */
	@Update("UPDATE application_record SET status=#{app.status}, " +
			"previous_active_status=#{app.previousActiveStatus, jdbcType=VARCHAR}, " +
			"updated_at=#{app.updatedAt} " +
			"WHERE id=#{app.id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateStatusAndPreviousByIdAndVersion(@Param("app") Application app,
											  @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE application_record SET version = version + 1 " +
			"WHERE id = #{id} AND version = #{expectedVersion} AND deleted_at IS NULL")
	int bumpVersionByIdAndVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);

	/** dashboard：所有活动投递（含 nextAction 状态），用于行动识别与 activeApplications。
	 *  排序：缺失/逾期行动在前，便于 service 层生成 actionItems。 */
	@Select("SELECT * FROM application_record WHERE deleted_at IS NULL " +
			"AND status IN ('DRAFT','APPLIED','RESUME_PASSED','INTERVIEWING','ON_HOLD') " +
			"ORDER BY (next_action IS NULL OR next_action = '') DESC, next_action_due_at ASC, updated_at DESC")
	List<Application> selectActiveForDashboard();
}
