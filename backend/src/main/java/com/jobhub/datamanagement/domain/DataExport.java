package com.jobhub.datamanagement.domain;

/**
 * 数据导出任务。P0 在创建请求内同步完成导出：QUEUED -> RUNNING -> SUCCEEDED/FAILED。
 * downloadPath 为服务端文件路径，仅由服务写入，不对客户端暴露。
 */
public class DataExport {
	private String id;
	private String format;
	private String status;
	private String downloadPath;
	private String failureReason;
	private String createdAt;
	private String updatedAt;

	public static DataExport create(String id, String format, String now) {
		DataExport export = new DataExport();
		export.id = id;
		export.format = format;
		export.status = "QUEUED";
		export.createdAt = now;
		export.updatedAt = now;
		return export;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getFormat() { return format; }
	public void setFormat(String format) { this.format = format; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public String getDownloadPath() { return downloadPath; }
	public void setDownloadPath(String downloadPath) { this.downloadPath = downloadPath; }
	public String getFailureReason() { return failureReason; }
	public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
	public String getCreatedAt() { return createdAt; }
	public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
