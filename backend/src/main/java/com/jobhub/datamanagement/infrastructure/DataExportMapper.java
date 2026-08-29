package com.jobhub.datamanagement.infrastructure;

import com.jobhub.datamanagement.domain.DataExport;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DataExportMapper {
	@Insert("""
		INSERT INTO data_export (id, format, status, created_at, updated_at)
		VALUES (#{id}, #{format}, #{status}, #{createdAt}, #{updatedAt})
		""")
	int insert(DataExport export);

	@Select("""
		SELECT id, format, status, download_path, failure_reason, created_at, updated_at
		FROM data_export
		WHERE id=#{id}
		""")
	DataExport selectById(@Param("id") String id);

	@Update("UPDATE data_export SET status=#{status}, updated_at=#{now} WHERE id=#{id}")
	int updateStatus(@Param("id") String id, @Param("status") String status, @Param("now") String now);

	@Update("""
		UPDATE data_export
		SET status='SUCCEEDED', download_path=#{downloadPath}, updated_at=#{now}
		WHERE id=#{id}
		""")
	int complete(@Param("id") String id, @Param("downloadPath") String downloadPath, @Param("now") String now);

	@Update("""
		UPDATE data_export
		SET status='FAILED', failure_reason=#{failureReason}, updated_at=#{now}
		WHERE id=#{id}
		""")
	int fail(@Param("id") String id, @Param("failureReason") String failureReason, @Param("now") String now);
}
