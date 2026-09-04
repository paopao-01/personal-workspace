package com.jobhub.mockinterview.infrastructure;
import com.jobhub.mockinterview.domain.*; import org.apache.ibatis.annotations.*; import java.util.*;
@Mapper public interface MockInterviewMapper {
 @Insert("INSERT INTO mock_interview_session (id,project_id,status,ai_job_id,project_snapshot,created_at,updated_at,version) VALUES (#{id},#{projectId},#{status},#{aiJobId},#{projectSnapshot},#{createdAt},#{updatedAt},0)") int insert(MockInterviewSession s);
 @Select("SELECT id,project_id AS projectId,status,ai_job_id AS aiJobId,project_snapshot AS projectSnapshot,created_at AS createdAt,updated_at AS updatedAt,version FROM mock_interview_session WHERE id=#{id}") MockInterviewSession selectById(String id);
 @Update("UPDATE mock_interview_session SET ai_job_id=#{aiJobId},updated_at=#{now} WHERE id=#{id}") int setAiJobId(@Param("id") String id,@Param("aiJobId") String aiJobId,@Param("now") String now);
 @Update("UPDATE mock_interview_session SET status='ACTIVE',updated_at=#{now},version=version+1 WHERE id=#{id} AND status='DRAFT'") int activate(@Param("id") String id,@Param("now") String now);
 @Update("UPDATE mock_interview_session SET status=#{status},updated_at=#{now},version=version+1 WHERE id=#{id} AND status=#{expected} AND version=#{version}") int transition(@Param("id") String id,@Param("status") String status,@Param("expected") String expected,@Param("version") long version,@Param("now") String now);
 @Insert("INSERT INTO mock_interview_turn (id,session_id,turn_number,speaker,content,created_at) VALUES (#{id},#{sessionId},#{turnNumber},#{speaker},#{content},#{createdAt})") int insertTurn(MockInterviewTurn t);
 @Select("SELECT id,session_id AS sessionId,turn_number AS turnNumber,speaker,content,created_at AS createdAt FROM mock_interview_turn WHERE session_id=#{sessionId} ORDER BY turn_number") List<MockInterviewTurn> selectTurns(String sessionId);
 @Select("SELECT COUNT(*) FROM mock_interview_turn WHERE session_id=#{sessionId}") int countTurns(String sessionId);
 @Select("SELECT COUNT(*) FROM mock_interview_turn WHERE session_id=#{sessionId} AND speaker='USER'") int countUserTurns(String sessionId);
 @Select("SELECT COALESCE(MAX(turn_number), 0) + 1 FROM mock_interview_turn WHERE session_id=#{sessionId}") int nextTurnNumber(String sessionId);
 @Update("UPDATE mock_interview_session SET updated_at=#{now},version=version+1 WHERE id=#{id} AND status='ACTIVE' AND version=#{version}") int incrementVersionForAnswer(@Param("id") String id,@Param("version") long version,@Param("now") String now);
}
