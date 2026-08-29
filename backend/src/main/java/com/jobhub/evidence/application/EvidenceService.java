package com.jobhub.evidence.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.evidence.domain.Evidence;
import com.jobhub.evidence.infrastructure.EvidenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class EvidenceService {
	private final EvidenceMapper evidenceMapper;
	private final IdGenerator ids;
	private final UtcTime time;

	public EvidenceService(EvidenceMapper evidenceMapper, IdGenerator ids, UtcTime time) {
		this.evidenceMapper = evidenceMapper;
		this.ids = ids;
		this.time = time;
	}

	public List<Evidence> list() {
		return evidenceMapper.selectAll().stream().map(this::hydrate).toList();
	}

	@Transactional
	public Evidence create(EvidenceCreateCommand cmd) {
		String now = time.now();
		Evidence evidence = Evidence.create(ids.newId(), cmd.type(),
			requiredText(cmd.title(), "Evidence title is required"), blankToNull(cmd.whereUsed()),
			blankToNull(cmd.problemSolved()), blankToNull(cmd.approach()), blankToNull(cmd.result()),
			blankToNull(cmd.urlOrPath()), now);
		evidenceMapper.insert(evidence);
		replaceSkillRefs(evidence.getId(), cmd.skillIds(), now);
		return get(evidence.getId());
	}

	@Transactional
	public Evidence update(String id, long expectedVersion, EvidenceCreateCommand cmd) {
		Evidence evidence = requireEvidence(id);
		evidence.updateMeta(cmd.type(), requiredText(cmd.title(), "Evidence title is required"),
			blankToNull(cmd.whereUsed()), blankToNull(cmd.problemSolved()), blankToNull(cmd.approach()),
			blankToNull(cmd.result()), blankToNull(cmd.urlOrPath()), time.now());
		VersionCheck.requireAffected(evidenceMapper.updateMeta(evidence, expectedVersion), evidence.getVersion());
		VersionCheck.requireAffected(evidenceMapper.bumpVersion(id, expectedVersion), evidence.getVersion());
		replaceSkillRefs(id, cmd.skillIds(), time.now());
		return get(id);
	}

	private void replaceSkillRefs(String evidenceId, List<String> skillIds, String now) {
		evidenceMapper.deleteSkillRefs(evidenceId);
		for (String skillId : distinct(skillIds)) {
			if (evidenceMapper.countActiveSkill(skillId) == 0) {
				throw new BusinessRuleException("Skill " + skillId + " does not exist or has been deleted");
			}
			evidenceMapper.insertSkillRef(skillId, evidenceId, now);
		}
	}

	private Evidence hydrate(Evidence evidence) {
		evidence.setSkillIds(evidenceMapper.selectSkillIds(evidence.getId()));
		return evidence;
	}

	private Evidence requireEvidence(String id) {
		Evidence evidence = evidenceMapper.selectById(id);
		VersionCheck.requireFound(evidence, "Evidence", id);
		return evidence;
	}

	private Evidence get(String id) {
		return hydrate(requireEvidence(id));
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
