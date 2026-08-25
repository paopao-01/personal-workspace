package com.jobhub.job.api;

import com.jobhub.job.domain.ConfirmationStatus;
import com.jobhub.job.domain.GapStatus;
import com.jobhub.job.domain.RequirementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RequirementUpdateRequest {

	@NotNull
	private ConfirmationStatus confirmationStatus;

	@Size(max = 2000)
	private String rawText;

	@Size(max = 200)
	private String normalizedName;

	private RequirementType type;

	@Size(max = 500)
	private String proficiencyText;

	@Size(max = 1000)
	private String reason;

	private GapStatus manualMatchStatus;

	public ConfirmationStatus getConfirmationStatus() { return confirmationStatus; }
	public void setConfirmationStatus(ConfirmationStatus confirmationStatus) { this.confirmationStatus = confirmationStatus; }
	public String getRawText() { return rawText; }
	public void setRawText(String rawText) { this.rawText = rawText; }
	public String getNormalizedName() { return normalizedName; }
	public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
	public RequirementType getType() { return type; }
	public void setType(RequirementType type) { this.type = type; }
	public String getProficiencyText() { return proficiencyText; }
	public void setProficiencyText(String proficiencyText) { this.proficiencyText = proficiencyText; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
	public GapStatus getManualMatchStatus() { return manualMatchStatus; }
	public void setManualMatchStatus(GapStatus manualMatchStatus) { this.manualMatchStatus = manualMatchStatus; }
}
