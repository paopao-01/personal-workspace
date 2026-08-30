package com.jobhub.job.api;

import com.jobhub.job.application.MatchReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 可解释岗位匹配报告（PRD 9.1）。生成保存快照；latest 返回最新报告并附读取时计算的 stale 标记。
 */
@RestController
@RequestMapping("/api")
public class JobMatchReportController {

	private final MatchReportService service;

	public JobMatchReportController(MatchReportService service) {
		this.service = service;
	}

	@PostMapping("/jobs/{jobId}/match-reports")
	public ResponseEntity<MatchReportResponse> generate(@PathVariable String jobId) {
		MatchReportResponse response = MatchReportResponse.from(service.generate(jobId));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/jobs/{jobId}/match-reports")
	public ResponseEntity<java.util.List<MatchReportResponse>> history(@PathVariable String jobId) {
		return ResponseEntity.ok(service.history(jobId).stream().map(MatchReportResponse::from).toList());
	}

	@GetMapping("/jobs/{jobId}/match-reports/latest")
	public ResponseEntity<MatchReportResponse> latest(@PathVariable String jobId) {
		return ResponseEntity.ok(MatchReportResponse.from(service.latest(jobId)));
	}
}
