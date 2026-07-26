package com.zhineng.platform.basicinfo.evaluationarchive.controller;

import com.zhineng.platform.basicinfo.evaluationarchive.dto.EvaluationArchiveDtos;
import com.zhineng.platform.basicinfo.evaluationarchive.service.EvaluationArchiveService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/basic-info")
public class EvaluationArchiveController {
    private final EvaluationArchiveService service;

    public EvaluationArchiveController(EvaluationArchiveService service) {
        this.service = service;
    }

    @GetMapping("/evaluation-archives")
    public EvaluationArchiveDtos.Page page(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String accessLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.page(orgId, year, type, grade, status, accessLevel, keyword, page, size);
    }

    @GetMapping("/evaluation-archives/stats")
    public EvaluationArchiveDtos.Stats stats() {
        return service.stats();
    }

    @GetMapping("/evaluation-archives/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping("/evaluation-archives")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody EvaluationArchiveDtos.SaveRequest request) {
        return service.create(request);
    }

    @PutMapping("/evaluation-archives/{id}")
    public Map<String, Object> update(
            @PathVariable long id, @RequestBody EvaluationArchiveDtos.SaveRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/evaluation-archives/{id}/archive")
    public Map<String, Object> archive(
            @PathVariable long id, @RequestBody EvaluationArchiveDtos.VersionRequest request
    ) {
        return service.archive(id, request);
    }

    @PostMapping("/evaluation-archives/{id}/withdraw")
    public Map<String, Object> withdraw(
            @PathVariable long id, @RequestBody EvaluationArchiveDtos.WithdrawRequest request
    ) {
        return service.withdraw(id, request);
    }

    @GetMapping("/evaluation-archives/{id}/attachments")
    public List<Map<String, Object>> attachments(
            @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean history
    ) {
        return service.attachments(id, history);
    }

    @PostMapping(
            value = "/evaluation-archives/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> upload(
            @PathVariable long id,
            @RequestPart("category") String category,
            @RequestPart(value = "remarks", required = false) String remarks,
            @RequestPart("file") MultipartFile file
    ) {
        return service.upload(id, category, remarks, file);
    }

    @PostMapping(
            value = "/evaluation-archives/{id}/attachments/{attachmentId}/replace",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Map<String, Object> replace(
            @PathVariable long id,
            @PathVariable long attachmentId,
            @RequestPart(value = "remarks", required = false) String remarks,
            @RequestPart("file") MultipartFile file
    ) {
        return service.replace(id, attachmentId, remarks, file);
    }

    @PutMapping("/evaluation-archives/{id}/attachments/{attachmentId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(
            @PathVariable long id,
            @PathVariable long attachmentId,
            @RequestBody EvaluationArchiveDtos.StatusRequest request
    ) {
        service.updateAttachmentStatus(id, attachmentId, request);
    }

    @GetMapping("/evaluation-archive-attachments/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable long id)
            throws java.io.IOException {
        return file(service.download(id, false), false);
    }

    @GetMapping("/evaluation-archive-attachments/{id}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable long id)
            throws java.io.IOException {
        return file(service.download(id, true), true);
    }

    private ResponseEntity<InputStreamResource> file(
            EvaluationArchiveService.Download file, boolean inline
    ) throws java.io.IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        ContentDisposition disposition = (inline
                ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        headers.setContentDisposition(disposition);
        try {
            headers.setContentType(MediaType.parseMediaType(file.contentType()));
        } catch (Exception ignored) {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        headers.setContentLength(java.nio.file.Files.size(file.path()));
        return new ResponseEntity<>(
                new InputStreamResource(java.nio.file.Files.newInputStream(file.path())),
                headers, HttpStatus.OK);
    }
}
