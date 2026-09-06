package com.jobhub.backup.infrastructure;

import com.jobhub.backup.domain.BackupRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.type.JdbcType;

import java.util.List;

@Mapper
public interface BackupRecordMapper {
	@Insert("""
		INSERT INTO backup_record (id, created_at, algorithm, pbkdf2_iterations, salt, iv,
			data_export_id, file_path, file_name, size_bytes)
		VALUES (#{id}, #{createdAt}, #{algorithm}, #{pbkdf2Iterations}, #{salt}, #{iv},
			#{dataExportId}, #{filePath}, #{fileName}, #{sizeBytes})
		""")
	int insert(BackupRecord record);

	@Select("""
		SELECT id, created_at, algorithm, pbkdf2_iterations, salt, iv, data_export_id,
			file_path, file_name, size_bytes
		FROM backup_record WHERE id = #{id}
		""")
	@Result(column = "salt", property = "salt", jdbcType = JdbcType.VARBINARY)
	@Result(column = "iv", property = "iv", jdbcType = JdbcType.VARBINARY)
	BackupRecord selectById(@Param("id") String id);

	@Select("""
		SELECT id, created_at, algorithm, pbkdf2_iterations, salt, iv, data_export_id,
			file_path, file_name, size_bytes
		FROM backup_record ORDER BY created_at DESC
		""")
	@Result(column = "salt", property = "salt", jdbcType = JdbcType.VARBINARY)
	@Result(column = "iv", property = "iv", jdbcType = JdbcType.VARBINARY)
	List<BackupRecord> selectList();
}

