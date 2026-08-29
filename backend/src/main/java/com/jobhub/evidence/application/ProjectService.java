package com.jobhub.evidence.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.datamanagement.application.TrashService;
import com.jobhub.evidence.domain.Evidence;
import com.jobhub.evidence.domain.ProjectCase;
import com.jobhub.evidence.infrastructure.EvidenceMapper;
import com.jobhub.evidence.infrastructure.ProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectService {
	private final ProjectMapper projectMapper;
	private final EvidenceMapper evidenceMapper;
	private final TrashService trashService;
	private final IdGenerator ids;
	private final UtcTime time;

	public ProjectService(ProjectMapper projectMapper, EvidenceMapper evidenceMapper, TrashService trashService,
			IdGenerator ids, UtcTime time) {
		this.projectMapper = projectMapper;
		this.evidenceMapper = evidenceMapper;
		this.trashService = trashService;
		this.ids = ids;
		this.time = time;
	}

	public List<ProjectCase> list() {
		return projectMapper.selectAll().stream().map(this::hydrate).toList();
	}

	@Transactional
	public ProjectCase create(ProjectCreateCommand cmd) {
		String now = time.now();
		ProjectCase project = ProjectCase.create(ids.newId(), requiredText(cmd.title(), "Project title is required"),
			requiredText(cmd.scenario(), "Project scenario is required"),
			requiredText(cmd.approach(), "Project approach is required"),
			requiredText(cmd.problemSolved(), "Project problemSolved is required"),
			blankToNull(cmd.result()), now);
		projectMapper.insert(project);
		replaceEvidenceRefs(project.getId(), cmd.evidenceIds(), now);
		return get(project.getId());
	}

	@Transactional
	public ProjectCase update(String id, long expectedVersion, ProjectCreateCommand cmd) {
		ProjectCase project = requireProject(id);
		project.updateMeta(requiredText(cmd.title(), "Project title is required"),
			requiredText(cmd.scenario(), "Project scenario is required"),
			requiredText(cmd.approach(), "Project approach is required"),
			requiredText(cmd.problemSolved(), "Project problemSolved is required"),
			blankToNull(cmd.result()), time.now());
		VersionCheck.requireAffected(projectMapper.updateMeta(project, expectedVersion), project.getVersion());
		VersionCheck.requireAffected(projectMapper.bumpVersion(id, expectedVersion), project.getVersion());
		replaceEvidenceRefs(id, cmd.evidenceIds(), time.now());
		return get(id);
	}

	@Transactional
	public void delete(String id, long expectedVersion) {
		ProjectCase project = requireProject(id);
		long evidenceRefCount = projectMapper.countEvidenceRefs(id);
		String now = time.now();
		VersionCheck.requireAffected(projectMapper.softDelete(id, expectedVersion, now), project.getVersion());
		trashService.recordDeletion(TrashService.TYPE_PROJECT_CASE, id, project.getTitle(),
			List.of(evidenceRefCount + " 条证据引用"), now);
	}

	private void replaceEvidenceRefs(String projectId, List<String> evidenceIds, String now) {
		projectMapper.deleteEvidenceRefs(projectId);
		for (String evidenceId : distinct(evidenceIds)) {
			// 校验忽略软删状态：已删证据的关联保留并在恢复后自动还原
			Evidence evidence = evidenceMapper.selectByIdIncludeTrashed(evidenceId);
			if (evidence == null) {
				throw new BusinessRuleException("Evidence " + evidenceId + " does not exist");
			}
			projectMapper.insertEvidenceRef(projectId, evidenceId, now);
		}
	}

	private ProjectCase hydrate(ProjectCase project) {
		project.setEvidenceRefs(projectMapper.selectEvidenceRefs(project.getId()));
		return project;
	}

	private ProjectCase requireProject(String id) {
		ProjectCase project = projectMapper.selectById(id);
		VersionCheck.requireFound(project, "ProjectCase", id);
		return project;
	}

	private ProjectCase get(String id) {
		return hydrate(requireProject(id));
	}

	private Set<String> distinct(List<String> values) {
		if (values == null) return Set.of();
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String value : values) {
			String normalized = blankToNull(value);
			if (normalized != null) result.add(normalized);
		}
		return result;
	}

	private String requiredText(String value, String message) {
		String normalized = blankToNull(value);
		if (normalized == null) {
			throw new BusinessRuleException(message);
		}
		return normalized;
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
