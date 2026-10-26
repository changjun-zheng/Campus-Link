package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.module.account.application.cmd.RosterImportResult;
import com.campuslink.module.account.domain.gateway.RosterRepository;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.model.StudentRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 学籍名册 CSV 导入用例（技术方案 4.3）：“学号,姓名[,年级[,专业]]”，首行表头自动跳过；
 * 名册哈希后入库（saveIfAbsent 跳过已存在学号），导入操作写审计日志（合规硬要求）。
 * 始终写库，不受 bypass 策略影响（bypass 只切换核验查询来源）。
 *
 * <p>N-6（CR-028）：表头识别支持中文「学号」与英文别名（student id / student_id 等，大小写与分隔符不敏感）；
 * 逐行校验学号 {@code ^\d{9}$}，不合格行计入 failed 且**不入库**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RosterImportApplicationService {

    /** 与注册链路同一学号规则（CR-013）；非法行不入库，防"英文表头被当学籍记录"这类脏数据 */
    private static final Pattern STUDENT_ID = Pattern.compile("^\\d{9}$");

    private final RosterRepository rosterRepository;
    private final SensitiveCodec codec;
    private final AuditService auditService;

    @Transactional
    public RosterImportResult importCsv(List<String> lines, String batch, Long operatorId) {
        int inserted = 0;
        int skipped = 0;
        int failed = 0;
        boolean firstLine = true;

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] cols = line.trim().split(",", -1);
            if (firstLine) {
                firstLine = false;
                if (isHeader(cols)) {
                    continue;
                }
            }
            if (cols.length < 2 || cols[0].isBlank() || cols[1].isBlank()) {
                failed++;
                continue;
            }
            String studentId = cols[0].trim();
            String name = cols[1].trim();
            if (!STUDENT_ID.matcher(studentId).matches()) {
                failed++;
                continue;
            }
            StudentRecord record = new StudentRecord(
                    null,
                    codec.hash(studentId),
                    name,
                    cols.length > 2 && !cols[2].isBlank() ? cols[2].trim() : null,
                    cols.length > 3 && !cols[3].isBlank() ? cols[3].trim() : null,
                    batch,
                    null);
            if (rosterRepository.saveIfAbsent(record)) {
                inserted++;
            } else {
                skipped++;
            }
        }

        auditService.record(operatorId, "ROSTER_IMPORT", "student_roster", null,
                "batch=%s inserted=%d skipped=%d failed=%d".formatted(batch, inserted, skipped, failed));
        return new RosterImportResult(inserted, skipped, failed);
    }

    /**
     * 表头识别（N-6）：首列含「学号」，或归一化后等于 {@code studentid}
     * （大小写、下划线 / 横线 / 空格均不敏感，覆盖 Student ID / student_id / student-id 等写法）。
     */
    private static boolean isHeader(String[] cols) {
        if (cols.length == 0) {
            return false;
        }
        String first = cols[0].trim();
        String normalized = first.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
        return first.contains("学号") || "studentid".equals(normalized);
    }
}
