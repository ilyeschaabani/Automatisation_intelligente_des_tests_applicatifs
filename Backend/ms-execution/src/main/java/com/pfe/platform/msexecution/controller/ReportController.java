package com.pfe.platform.msexecution.controller;

import com.pfe.platform.msexecution.entity.Report;
import com.pfe.platform.msexecution.service.ReportService;
import com.pfe.platform.msexecution.service.ReportStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportStorageService reportStorageService;

    @GetMapping
    public List<Report> listAll() {
        return reportStorageService.listAllReports();
    }

    @GetMapping("/campaign/{campaignId}")
    public List<Report> listForCampaign(@PathVariable Long campaignId) {
        return reportStorageService.listReportsForCampaign(campaignId);
    }

    @PostMapping("/campaign/{campaignId}")
    public Report generateAndStore(@PathVariable Long campaignId, @RequestHeader(name = "Authorization", required = false) String authorization) {
        byte[] pdf = reportService.generateCampaignReport(campaignId, authorization);
        String filename = "campaign-" + campaignId + "-report.pdf";
        return reportStorageService.storeReport(campaignId, pdf, filename);
    }

    @GetMapping("/{reportId}/pdf")
    public ResponseEntity<byte[]> downloadById(@PathVariable Long reportId) throws Exception {
        Report r = reportStorageService.listAllReports().stream().filter(rep -> rep.getId().equals(reportId)).findFirst().orElse(null);
        if (r == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        Path p = Path.of(r.getFilePath());
        if (!Files.exists(p)) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        byte[] bytes = Files.readAllBytes(p);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(r.getFilename()).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @GetMapping("/campaign/{campaignId}/pdf")
    public ResponseEntity<byte[]> generateOnDemand(@PathVariable Long campaignId, @RequestHeader(name = "Authorization", required = false) String authorization) {
        byte[] pdf = reportService.generateCampaignReport(campaignId, authorization);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("campaign-" + campaignId + "-report.pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
 