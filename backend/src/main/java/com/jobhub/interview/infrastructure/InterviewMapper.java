package com.jobhub.interview.infrastructure;

import com.jobhub.interview.domain.Interview;
import com.jobhub.interview.domain.InterviewScheduleStatus;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface InterviewMapper {
    @Insert("INSERT INTO interview_schedule (id, application_id, round_name, starts_at, event_time_zone, mode, meeting_url_or_address, contact, schedule_status, result, notes, created_at, updated_at, version) VALUES (#{id},#{applicationId},#{roundName},#{startsAt},#{eventTimeZone},#{mode},#{meetingUrlOrAddress},#{contact},#{scheduleStatus},#{result},#{notes},#{createdAt},#{updatedAt},#{version})")
    int insert(Interview i);
    @Select("SELECT * FROM interview_schedule WHERE id=#{id} AND deleted_at IS NULL") Interview selectById(@Param("id") String id);
    @Select("SELECT * FROM interview_schedule WHERE application_id=#{applicationId} AND deleted_at IS NULL ORDER BY starts_at") List<Interview> selectByApplication(@Param("applicationId") String applicationId);
    @Select("SELECT * FROM interview_schedule WHERE deleted_at IS NULL AND starts_at >= #{from} AND starts_at <= #{to} AND (#{status} IS NULL OR schedule_status=#{status}) ORDER BY starts_at") List<Interview> selectUpcoming(@Param("from") String from, @Param("to") String to, @Param("status") InterviewScheduleStatus status);
    @Update("UPDATE interview_schedule SET round_name=#{i.roundName}, mode=#{i.mode}, meeting_url_or_address=#{i.meetingUrlOrAddress}, contact=#{i.contact}, result=#{i.result}, notes=#{i.notes}, updated_at=#{i.updatedAt} WHERE id=#{i.id} AND version=#{expectedVersion} AND deleted_at IS NULL") int updateMeta(@Param("i") Interview i, @Param("expectedVersion") long expectedVersion);
    @Update("UPDATE interview_schedule SET starts_at=#{i.startsAt}, event_time_zone=#{i.eventTimeZone}, updated_at=#{i.updatedAt} WHERE id=#{i.id} AND version=#{expectedVersion} AND deleted_at IS NULL") int updateSchedule(@Param("i") Interview i, @Param("expectedVersion") long expectedVersion);
    @Update("UPDATE interview_schedule SET schedule_status=#{i.scheduleStatus}, result=#{i.result}, updated_at=#{i.updatedAt} WHERE id=#{i.id} AND version=#{expectedVersion} AND deleted_at IS NULL") int updateState(@Param("i") Interview i, @Param("expectedVersion") long expectedVersion);
    @Update("UPDATE interview_schedule SET version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL") int bumpVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);
}
