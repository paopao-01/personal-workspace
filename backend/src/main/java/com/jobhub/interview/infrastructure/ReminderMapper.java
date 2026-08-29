package com.jobhub.interview.infrastructure;
import com.jobhub.interview.domain.*;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface ReminderMapper {
    @Insert("INSERT INTO interview_reminder (id, interview_id, reminder_type, scheduled_at, status, created_at, updated_at, version) VALUES (#{id},#{interviewId},#{reminderType},#{scheduledAt},#{status},#{createdAt},#{updatedAt},#{version})") int insert(Reminder r);
    @Select("SELECT * FROM interview_reminder WHERE interview_id=#{interviewId} ORDER BY scheduled_at") List<Reminder> selectByInterview(@Param("interviewId") String interviewId);
    @Select("SELECT * FROM interview_reminder WHERE id=#{id}") Reminder selectById(@Param("id") String id);
    @Update("UPDATE interview_reminder SET status='CANCELED', updated_at=#{now}, version=version+1 WHERE interview_id=#{interviewId} AND status IN ('PENDING','PROCESSING')") int cancelOpen(@Param("interviewId") String interviewId,@Param("now") String now);
    @Update("UPDATE interview_reminder SET scheduled_at=#{r.scheduledAt}, status=#{r.status}, updated_at=#{r.updatedAt} WHERE id=#{r.id} AND version=#{expectedVersion}") int update(@Param("r") Reminder r,@Param("expectedVersion") long expectedVersion);
    @Select("SELECT r.id, r.interview_id AS interviewId, r.reminder_type AS reminderType, r.scheduled_at AS scheduledAt, i.round_name AS roundName FROM interview_reminder r JOIN interview_schedule i ON i.id = r.interview_id WHERE r.status='PENDING' AND r.scheduled_at <= #{now} ORDER BY r.scheduled_at LIMIT 200") List<DueReminderRow> selectDue(@Param("now") String now);
    @Update("UPDATE interview_reminder SET status='SENT', updated_at=#{now} WHERE id=#{id} AND status='PENDING'") int markSent(@Param("id") String id,@Param("now") String now);
}
