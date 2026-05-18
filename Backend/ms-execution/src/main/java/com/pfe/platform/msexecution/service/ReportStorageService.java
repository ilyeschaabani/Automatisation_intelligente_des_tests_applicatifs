package com.pfe.platform.msexecution.service;

import com.pfe.platform.msexecution.entity.Report;
import com.pfe.platform.msexecution.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportStorageService {

    private final ReportService reportService;
    private final ReportRepository reportRepository;

    @Value("${reports.storage.path:./data/reports}")
    private String reportsStoragePath;

    @Transactional
    public Report storeReport(Long campaignId, byte[] pdfBytes, String filename) {
        try {
            Path base = Paths.get(reportsStoragePath);
            if (!Files.exists(base)) {
                Files.createDirectories(base);
            }
            String unique = UUID.randomUUID().toString().substring(0, 8);
            String storedName = unique + "-" + filename;
            Path dest = base.resolve(storedName);
            Files.write(dest, pdfBytes);

            Report r = new Report(campaignId, filename, dest.toAbsolutePath().toString(), LocalDateTime.now());
            return reportRepository.save(r);
        } catch (IOException ex) {
            log.error("Failed to write report file", ex);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store report", ex);
        }
    }

    @Async
    public void generateAndStoreAsync(Long campaignId, String authorizationHeader) {
        try {
            byte[] pdf = reportService.generateCampaignReport(campaignId, authorizationHeader);
            String filename = "campaign-" + campaignId + "-report.pdf";
            storeReport(campaignId, pdf, filename);
            log.info("Generated and stored report for campaign {}", campaignId);
        } catch (Exception ex) {
            log.error("Failed to generate and store report for campaign {}: {}", campaignId, ex.getMessage());
        }
    }

    public List<Report> listReportsForCampaign(Long campaignId) {
        return reportRepository.findByCampaignIdOrderByGeneratedAtDesc(campaignId);
    }

    public List<Report> listAllReports() {
        return reportRepository.findAll();
    }
}
