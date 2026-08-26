package com.jobhub.interview.infrastructure;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface ChecklistMapper {
    @Insert("INSERT INTO interview_checklist_item (id, interview_id, text, completed, sort_order, created_at, updated_at) VALUES (#{id},#{interviewId},#{text},0,#{sortOrder},#{now},#{now})") int insert(@Param("id") String id,@Param("interviewId") String interviewId,@Param("text") String text,@Param("sortOrder") int sortOrder,@Param("now") String now);
    @Select("SELECT text FROM interview_checklist_item WHERE interview_id=#{interviewId} ORDER BY sort_order") List<String> selectTexts(@Param("interviewId") String interviewId);
    @Delete("DELETE FROM interview_checklist_item WHERE interview_id=#{interviewId}") int deleteByInterview(@Param("interviewId") String interviewId);
}
