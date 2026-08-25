package com.jobhub.job.application;

import com.jobhub.job.domain.JobRequirement;

import java.util.List;

public record ExtractionResult(List<JobRequirement> candidates, int newCount) { }
