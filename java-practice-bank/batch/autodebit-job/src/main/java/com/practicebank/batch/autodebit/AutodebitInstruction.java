package com.practicebank.batch.autodebit;

import java.time.LocalDate;

/**
 * autodebit_schedules テーブルの 1 行に対応する読み取り専用 DTO.
 * カラム名・型は DB スキーマ (V1__initial_schema.sql) と一致させる.
 */
public record AutodebitInstruction(
    String instructionId,
    String payerAccount,
    String payeeName,
    Long amountJpy,
    String frequency,
    LocalDate nextDueDate,
    String status,
    LocalDate lastAttemptDate,
    String lastAttemptResult,
    Integer consecutiveFailures
) {
    /** 次回プラン日を頻度から計算 (M=月次, W=週次, D=日次). */
    public LocalDate computeNextDueDate() {
        if (nextDueDate == null) return null;
        return switch (frequency == null ? "" : frequency.trim()) {
            case "M" -> nextDueDate.plusMonths(1);
            case "W" -> nextDueDate.plusWeeks(1);
            case "D" -> nextDueDate.plusDays(1);
            default -> nextDueDate.plusMonths(1);
        };
    }
}
