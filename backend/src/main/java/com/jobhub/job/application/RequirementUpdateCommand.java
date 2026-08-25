package com.jobhub.job.application;

import com.jobhub.job.domain.ConfirmationStatus;
import com.jobhub.job.domain.GapStatus;
import com.jobhub.job.domain.RequirementType;

public record RequirementUpdateCommand(
		ConfirmationStatus confirmationStatus,
		String rawText,
		String normalizedName,
		RequirementType type,
		String proficiencyText,
		String reason,
		GapStatus manualMatchStatus
) { }
