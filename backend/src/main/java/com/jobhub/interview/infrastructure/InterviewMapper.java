package com.jobhub.interview.infrastructure;

import com.jobhub.interview.domain.Interview;
import com.jobhub.interview.domain.InterviewListItem;
import com.jobhub.interview.domain.InterviewMode;
import com.jobhub.interview.domain.InterviewScheduleStatus;
import com.jobhub.application.domain.ApplicationStatus;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface InterviewMapper {
    @Insert("INSERT INTO interview_schedule (id, application_id, round_name, starts_at, event_time_zone, mode, meeting_url_or_address, contact, schedule_status, result, notes, created_at, updated_at, version) VALUES (#{id},#{applicationId},#{roundName},#{startsAt},#{eventTimeZone},#{mode},#{meetingUrlOrAddress},#{contact},#{scheduleStatus},#{result},#{notes},#{createdAt},#{updatedAt},#{version})")
    int insert(Interview i);
    @Select("SELECT * FROM interview_schedule WHERE id=#{id} AND deleted_at IS NULL") Interview selectById(@Param("id") String id);
    @Select("SELECT * FROM interview_schedule WHERE application_id=#{applicationId} AND deleted_at IS NULL ORDER BY starts_at") List<Interview> selectByApplication(@Param("applicationId") String applicationId);
    @Select("SELECT * FROM interview_schedule WHERE deleted_at IS NULL AND starts_at >= #{from} AND starts_at <= #{to} AND (#{status} IS NULL OR schedule_status=#{status}) ORDER BY starts_at") List<Interview> selectUpcoming(@Param("from") String from, @Param("to") String to, @Param("status") InterviewScheduleStatus status);
    @Select("SELECT i.* FROM interview_schedule i LEFT JOIN interview_review r ON r.interview_id=i.id AND r.deleted_at IS NULL " +
            "WHERE i.deleted_at IS NULL AND i.schedule_status='COMPLETED' AND (r.id IS NULL OR r.review_status != 'COMPLETED') ORDER BY i.starts_at DESC")
    List<Interview> selectCompletedNeedingReview();
    @Select("<script>" +
            "SELECT i.*, a.status AS application_status, a.job_id, j.company_name, j.title AS job_title " +
            "FROM interview_schedule i " +
            "JOIN application_record a ON a.id = i.application_id AND a.deleted_at IS NULL " +
            "JOIN job_posting j ON j.id = a.job_id AND j.deleted_at IS NULL " +
            "WHERE i.deleted_at IS NULL AND i.starts_at &gt;= #{from} AND i.starts_at &lt;= #{to} " +
            "<if test='scheduleStatus != null'>AND i.schedule_status = #{scheduleStatus} </if>" +
            "<if test='applicationStatus != null'>AND a.status = #{applicationStatus} </if>" +
            "<if test='mode != null'>AND i.mode = #{mode} </if>" +
            "ORDER BY i.starts_at" +
            "</script>")
    List<InterviewListItem> selectListItems(@Param("from") String from, @Param("to") String to,
            @Param("scheduleStatus") InterviewScheduleStatus scheduleStatus,
            @Param("applicationStatus") ApplicationStatus applicationStatus,
            @Param("mode") InterviewMode mode);
    @Update("UPDATE interview_schedule SET round_name=#{i.roundName}, mode=#{i.mode}, meeting_url_or_address=#{i.meetingUrlOrAddress}, contact=#{i.contact}, result=#{i.result}, notes=#{i.notes}, updated_at=#{i.updatedAt} WHERE id=#{i.id} AND version=#{expectedVersion} AND deleted_at IS NULL") int updateMeta(@Param("i") Interview i, @Param("expectedVersion") long expectedVersion);
    @Update("UPDATE interview_schedule SET starts_at=#{i.startsAt}, event_time_zone=#{i.eventTimeZone}, updated_at=#{i.updatedAt} WHERE id=#{i.id} AND version=#{expectedVersion} AND deleted_at IS NULL") int updateSchedule(@Param("i") Interview i, @Param("expectedVersion") long expectedVersion);
    @Update("UPDATE interview_schedule SET schedule_status=#{i.scheduleStatus}, result=#{i.result}, updated_at=#{i.updatedAt} WHERE id=#{i.id} AND version=#{expectedVersion} AND deleted_at IS NULL") int updateState(@Param("i") Interview i, @Param("expectedVersion") long expectedVersion);
    @Update("UPDATE interview_schedule SET version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL") int bumpVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);
    @Update("UPDATE interview_schedule SET deleted_at=#{now}, updated_at=#{now}, version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL") int softDelete(@Param("id") String id, @Param("expectedVersion") long expectedVersion, @Param("now") String now);
    @Update("UPDATE interview_schedule SET deleted_at=NULL, updated_at=#{now} WHERE id=#{id} AND deleted_at IS NOT NULL") int restoreById(@Param("id") String id, @Param("now") String now);
}
