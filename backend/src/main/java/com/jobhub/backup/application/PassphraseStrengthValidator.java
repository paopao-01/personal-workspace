package com.jobhub.backup.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * passphrase 强度评估与强制门槛（与前端 {@code passphraseStrength.ts} 同一算法，单一事实来源见
 * <a href="../../../../../../../docs/jobhub/02-state-machines.md">02-state-machines.md §9</a>）。
 *
 * <p>维度：长度分（上限 35）+ 字符种类分（上限 45）− 弱模式扣分（纯重复/常见弱口令/连续重复段）。
 * 阈值：弱 &lt; 40 / 中 40–69 / 强 ≥ 70。{@code score < 70}（即弱或中，未达 strong）抛
 * {@link BusinessRuleException}（{@link ErrorCode#VALIDATION_ERROR}，400），message 含 score 与失败规则。
 * 要求 strong：只有 {@code score ≥ 70} 放行，弱与中一律拒绝。
 *
 * <p>评估仅在调用栈内进行，passphrase 不落盘、不进日志、不回显，不新增评估端点。
 * 创建备份与武装调度入口调用本校验器；恢复端点豁免（passphrase 已与备份绑定）。
 */
public final class PassphraseStrengthValidator {

	/** 弱口令阈值下限：score < 40 即弱。 */
	static final int WEAK_THRESHOLD = 40;
	static final int STRONG_THRESHOLD = 70;
	/** 强制门槛要求等级：未达此等级一律拒绝（创建/武装入口）。 */
	private static final Level REQUIRED_LEVEL = Level.STRONG;

	/** 与前端一致的常见弱口令黑名单（小写前缀/整体匹配）。 */
	private static final List<String> COMMON_WEAK = List.of(
		"password", "passphrase", "123456", "12345678", "qwerty", "admin", "letmein", "welcome",
		"abc123", "11111111", "00000000", "12312312", "password123", "admin123", "root", "toor",
		"iloveyou", "monkey", "dragon", "login");

	private static final Set<Character> SYMBOLS = Set.of(
		'!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '_', '=', '+', '[', ']', '{', '}',
		';', ':', ',', '.', '<', '>', '?', '/', '|', '~', '`');

	private PassphraseStrengthValidator() {
	}

	/**
	 * 评估 passphrase 强度，返回 0–100 分与等级。
	 * 与前端算法逐行对齐：长度分 + 字符种类分 − 弱模式扣分，钳制 [0,100]。
	 */
	static Result evaluate(String passphrase) {
		String pw = passphrase == null ? "" : passphrase;
		if (pw.isEmpty()) {
			return new Result(0, Level.WEAK, List.of("输入 passphrase 以评估强度"));
		}

		int score = 0;

		// 1. 长度（上限 35）
		if (pw.length() >= 16) {
			score += 35;
		} else if (pw.length() >= 12) {
			score += 25;
		} else if (pw.length() >= 8) {
			score += 10;
		}
		// 长度 < 8 不给长度分（提交本就被长度校验拒）

		// 2. 字符种类（上限 45）
		boolean hasLower = false;
		boolean hasUpper = false;
		boolean hasDigit = false;
		boolean hasSymbol = false;
		for (int i = 0; i < pw.length(); i++) {
			char c = pw.charAt(i);
			if (c >= 'a' && c <= 'z') {
				hasLower = true;
			} else if (c >= 'A' && c <= 'Z') {
				hasUpper = true;
			} else if (c >= '0' && c <= '9') {
				hasDigit = true;
			} else if (SYMBOLS.contains(c)) {
				hasSymbol = true;
			}
		}
		if (hasLower) {
			score += 10;
		}
		if (hasUpper) {
			score += 10;
		}
		if (hasDigit) {
			score += 10;
		}
		if (hasSymbol) {
			score += 15;
		}

		// 3. 弱模式扣分
		String lowered = pw.toLowerCase();
		boolean allSame = pw.length() > 1 && allSame(pw);
		boolean inCommon = matchesCommonWeak(lowered);
		boolean hasRun = hasRepeatingRun(pw);

		if (allSame) {
			score -= 25;
		}
		if (inCommon) {
			score -= 30;
		}
		if (hasRun && !allSame) {
			score -= 10;
		}

		score = Math.max(0, Math.min(100, score));

		Level level = score >= STRONG_THRESHOLD ? Level.STRONG : score >= WEAK_THRESHOLD ? Level.FAIR : Level.WEAK;

		List<String> reasons = new ArrayList<>();
		if (pw.length() < 12) {
			reasons.add("长度不足");
		}
		if (!hasUpper) {
			reasons.add("缺大写字母");
		}
		if (!hasDigit) {
			reasons.add("缺数字");
		}
		if (!hasSymbol) {
			reasons.add("缺符号");
		}
		if (allSame || inCommon || hasRun) {
			reasons.add("命中弱模式");
		}
		if (level == Level.STRONG) {
			reasons.clear();
		}
		return new Result(score, level, List.copyOf(reasons));
	}

	/**
	 * 强制门槛校验（要求 strong）：{@code level != STRONG}（即弱或中，{@code score < 70}）抛
	 * {@link BusinessRuleException}（400 VALIDATION_ERROR），message 含 {@code score=X/100，需 ≥70}
	 * 与失败规则，不进行后续加密/落盘/武装。只有 {@code score ≥ 70}（强）放行。
	 */
	public static void requireAcceptable(String passphrase) {
		Result r = evaluate(passphrase);
		if (r.level() != REQUIRED_LEVEL) {
			throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
				"passphrase 强度不足：score=" + r.score() + "/100，需 ≥" + STRONG_THRESHOLD
					+ "（要求强口令）；失败规则：" + (r.reasons().isEmpty() ? "无" : String.join("、", r.reasons())));
		}
	}

	private static boolean allSame(String pw) {
		char first = pw.charAt(0);
		for (int i = 1; i < pw.length(); i++) {
			if (pw.charAt(i) != first) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesCommonWeak(String lowered) {
		for (String w : COMMON_WEAK) {
			if (lowered.equals(w) || lowered.startsWith(w)) {
				return true;
			}
		}
		return false;
	}

	/** 检测连续 3+ 相同字符（与前端正则 {@code /(.)\1{2,}/} 等价）。 */
	private static boolean hasRepeatingRun(String pw) {
		int run = 1;
		for (int i = 1; i < pw.length(); i++) {
			if (pw.charAt(i) == pw.charAt(i - 1)) {
				run++;
				if (run >= 3) {
					return true;
				}
			} else {
				run = 1;
			}
		}
		return false;
	}

	enum Level { WEAK, FAIR, STRONG }

	record Result(int score, Level level, List<String> reasons) { }
}
