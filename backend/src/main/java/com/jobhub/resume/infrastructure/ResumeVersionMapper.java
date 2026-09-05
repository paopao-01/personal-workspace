package com.jobhub.resume.infrastructure;
import com.jobhub.resume.domain.ResumeVersion; import java.util.*; import org.apache.ibatis.annotations.*;
@Mapper public interface ResumeVersionMapper {
 @Insert("INSERT INTO resume_version (id,name,content,created_at,updated_at,version) VALUES (#{id},#{name},#{content},#{createdAt},#{updatedAt},0)") int insert(ResumeVersion v);
 @Select("SELECT id,name,content,created_at AS createdAt,updated_at AS updatedAt,version FROM resume_version ORDER BY created_at DESC,id DESC") List<ResumeVersion> selectAll();
 @Select("SELECT id,name,content,created_at AS createdAt,updated_at AS updatedAt,version FROM resume_version WHERE id=#{id}") ResumeVersion selectById(String id);
}
