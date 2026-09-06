package com.jobhub.backup.infrastructure;

import com.jobhub.backup.domain.BackupRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
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

	/** 物理删除单条备份记录。返回受影响行数，0 表示记录不存在。 */
	@Delete("DELETE FROM backup_record WHERE id = #{id}")
	int deleteById(@Param("id") String id);

	/** 按龄清理：返回 created_at 早于阈值的全部记录（含 file_path 供文件清理）。ISO-8601 字符串字典序比较。 */
	@Select("""
		SELECT id, created_at, algorithm, pbkdf2_iterations, salt, iv, data_export_id,
			file_path, file_name, size_bytes
		FROM backup_record WHERE created_at < #{cutoff} ORDER BY created_at ASC
		""")
	@Result(column = "salt", property = "salt", jdbcType = JdbcType.VARBINARY)
	@Result(column = "iv", property = "iv", jdbcType = JdbcType.VARBINARY)
	List<BackupRecord> selectByCreatedBefore(@Param("cutoff") String cutoff);

	/** 返回全部现存 backup_record 的 file_name（孤儿扫描判定：.enc 文件名不在该集合即孤儿）。 */
	@Select("SELECT file_name FROM backup_record")
	List<String> selectAllFileNames();
}

