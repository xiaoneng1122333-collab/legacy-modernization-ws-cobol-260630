package com.practicebank.verify.comparator.controller;

import com.practicebank.verify.comparator.model.CompareReport;
import com.practicebank.verify.comparator.service.ComparatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * /api/compare/{businessDate} REST コントローラー.
 * COBOL 側と Java 側のスキーマ間 row count diff を JSON で返却する.
 */
@RestController
@RequestMapping("/api")
public class CompareController {

    private static final Logger LOG = LoggerFactory.getLogger(CompareController.class);

    private final ComparatorService service;

    public CompareController(ComparatorService service) {
        this.service = service;
    }

    @GetMapping("/compare/{businessDate}")
    public ResponseEntity<CompareReport> compare(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        LOG.info("compare request businessDate={}", businessDate);
        CompareReport report = service.compare(businessDate);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/compare")
    public ResponseEntity<CompareReport> compareToday() {
        return ResponseEntity.ok(service.compare(LocalDate.now()));
    }
}
