package com.jobhub.evidence.infrastructure;

import com.jobhub.evidence.domain.EvidenceAttachment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface EvidenceAttachmentMapper {
    @Insert("""
        INSERT INTO evidence_attachment (
          id, evidence_id, display_name, source_type, location, media_type, size_bytes, description,
          created_at, updated_at, version
        ) VALUES (
          #{id}, #{evidenceId}, #{displayName}, #{sourceType}, #{location}, #{mediaType}, #{sizeBytes}, #{description},
          #{createdAt}, #{updatedAt}, #{version}
        )
        """)
    int insert(EvidenceAttachment attachment);

    @Select("""
        <script>
        SELECT a.id, a.evidence_id, e.title AS evidence_title, a.display_name, a.source_type, a.location,
               a.media_type, a.size_bytes, a.description, a.created_at, a.updated_at, a.deleted_at, a.version
        FROM evidence_attachment a
        JOIN evidence e ON e.id = a.evidence_id
        WHERE a.deleted_at IS NULL AND e.deleted_at IS NULL
        <if test="evidenceId != null">AND a.evidence_id=#{evidenceId}</if>
        ORDER BY a.updated_at DESC, a.display_name
        </script>
        """)
    List<EvidenceAttachment> selectActive(@Param("evidenceId") String evidenceId);

    @Select("""
        SELECT a.id, a.evidence_id, e.title AS evidence_title, a.display_name, a.source_type, a.location,
               a.media_type, a.size_bytes, a.description, a.created_at, a.updated_at, a.deleted_at, a.version
        FROM evidence_attachment a
        JOIN evidence e ON e.id = a.evidence_id
        WHERE a.evidence_id=#{evidenceId}
        ORDER BY a.updated_at DESC, a.display_name
        """)
    List<EvidenceAttachment> selectByEvidenceIncludeTrashed(@Param("evidenceId") String evidenceId);

    @Select("""
        SELECT a.id, a.evidence_id, e.title AS evidence_title, a.display_name, a.source_type, a.location,
               a.media_type, a.size_bytes, a.description, a.created_at, a.updated_at, a.deleted_at, a.version
        FROM evidence_attachment a
        JOIN evidence e ON e.id = a.evidence_id
        WHERE a.id=#{id} AND a.deleted_at IS NULL
        """)
    EvidenceAttachment selectById(@Param("id") String id);

    @Update("""
        UPDATE evidence_attachment
        SET display_name=#{attachment.displayName}, source_type=#{attachment.sourceType},
            location=#{attachment.location}, media_type=#{attachment.mediaType}, size_bytes=#{attachment.sizeBytes},
            description=#{attachment.description}, updated_at=#{attachment.updatedAt}
        WHERE id=#{attachment.id} AND version=#{expectedVersion} AND deleted_at IS NULL
        """)
    int updateMeta(@Param("attachment") EvidenceAttachment attachment, @Param("expectedVersion") long expectedVersion);

    @Update("UPDATE evidence_attachment SET version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL")
    int bumpVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);

    @Update("""
        UPDATE evidence_attachment SET deleted_at=#{now}, updated_at=#{now}, version=version+1
        WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL
        """)
    int softDelete(@Param("id") String id, @Param("expectedVersion") long expectedVersion, @Param("now") String now);

    @Update("UPDATE evidence_attachment SET deleted_at=NULL, updated_at=#{now} WHERE id=#{id} AND deleted_at IS NOT NULL")
    int restoreById(@Param("id") String id, @Param("now") String now);

    @Delete("DELETE FROM evidence_attachment WHERE id=#{id}")
    int hardDelete(@Param("id") String id);

    @Delete("DELETE FROM evidence_attachment WHERE evidence_id=#{evidenceId}")
    int hardDeleteByEvidence(@Param("evidenceId") String evidenceId);
}
