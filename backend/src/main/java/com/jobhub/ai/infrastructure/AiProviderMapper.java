package com.jobhub.ai.infrastructure;

import com.jobhub.ai.domain.AiProvider;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiProviderMapper {
	@Select("""
		SELECT id, provider_type AS providerType, name, base_url AS baseUrl, model, api_key AS apiKey,
		       is_active AS active, created_at AS createdAt, updated_at AS updatedAt, version
		FROM ai_provider
		WHERE id=#{id}
		""")
	AiProvider selectById(@Param("id") String id);

	@Select("""
		SELECT id, provider_type AS providerType, name, base_url AS baseUrl, model, api_key AS apiKey,
		       is_active AS active, created_at AS createdAt, updated_at AS updatedAt, version
		FROM ai_provider
		ORDER BY created_at, id
		""")
	List<AiProvider> selectAll();

	@Select("SELECT id, provider_type AS providerType, name, base_url AS baseUrl, model, api_key AS apiKey, is_active AS active, created_at AS createdAt, updated_at AS updatedAt, version FROM ai_provider WHERE is_active=1 LIMIT 1")
	AiProvider selectActive();

	@Insert("""
		INSERT INTO ai_provider (id, provider_type, name, base_url, model, api_key, is_active, created_at, updated_at, version)
		VALUES (#{p.id}, #{p.providerType}, #{p.name}, #{p.baseUrl}, #{p.model}, #{p.apiKey}, #{p.active}, #{p.createdAt}, #{p.updatedAt}, 1)
		""")
	int insert(@Param("p") AiProvider provider);

	@Update("""
		UPDATE ai_provider
		SET name=#{p.name}, base_url=#{p.baseUrl}, model=#{p.model},
		    api_key=COALESCE(#{p.apiKey}, api_key), updated_at=#{p.updatedAt}, version=version+1
		WHERE id=#{p.id} AND version=#{expectedVersion}
		""")
	int update(@Param("p") AiProvider provider, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE ai_provider SET is_active=0, updated_at=#{now} WHERE is_active=1 AND id<>#{keepId}")
	int deactivateOthers(@Param("keepId") String keepId, @Param("now") String now);

	@Update("""
		UPDATE ai_provider SET is_active=#{p.active}, updated_at=#{p.updatedAt}, version=version+1
		WHERE id=#{p.id}
		""")
	int updateActive(@Param("p") AiProvider provider);

	@Delete("""
		DELETE FROM ai_provider
		WHERE id=#{id} AND version=#{expectedVersion} AND is_active=0
		  AND NOT EXISTS (SELECT 1 FROM ai_job WHERE provider_id=#{id})
		""")
	int deleteByIdAndVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);
}
