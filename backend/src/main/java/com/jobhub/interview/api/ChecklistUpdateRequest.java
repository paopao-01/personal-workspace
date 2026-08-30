package com.jobhub.interview.api;

import jakarta.validation.constraints.NotNull;

public record ChecklistUpdateRequest(@NotNull Boolean completed) { }
