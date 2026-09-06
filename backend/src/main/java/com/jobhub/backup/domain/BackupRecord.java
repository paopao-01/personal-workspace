package com.jobhub.backup.domain;

/**
 * 加密备份记录（追加型只读历史，无状态/版本字段）。
 * salt 与 iv 落库以供未来恢复切片从 passphrase 派生密钥；
 * passphrase 与派生密钥永不持久化。
 */
public class BackupRecord {
	private String id;
	private String createdAt;
	private String algorithm;
	private int pbkdf2Iterations;
	private byte[] salt;
	private byte[] iv;
	private String dataExportId;
	private String filePath;
	private String fileName;
	private long sizeBytes;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getCreatedAt() { return createdAt; }
	public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
	public String getAlgorithm() { return algorithm; }
	public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
	public int getPbkdf2Iterations() { return pbkdf2Iterations; }
	public void setPbkdf2Iterations(int pbkdf2Iterations) { this.pbkdf2Iterations = pbkdf2Iterations; }
	public byte[] getSalt() { return salt; }
	public void setSalt(byte[] salt) { this.salt = salt; }
	public byte[] getIv() { return iv; }
	public void setIv(byte[] iv) { this.iv = iv; }
	public String getDataExportId() { return dataExportId; }
	public void setDataExportId(String dataExportId) { this.dataExportId = dataExportId; }
	public String getFilePath() { return filePath; }
	public void setFilePath(String filePath) { this.filePath = filePath; }
	public String getFileName() { return fileName; }
	public void setFileName(String fileName) { this.fileName = fileName; }
	public long getSizeBytes() { return sizeBytes; }
	public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
}
