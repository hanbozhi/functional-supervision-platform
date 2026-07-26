package com.zhineng.platform.basicinfo.threefixedplan.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.basicinfo.threefixedplan.dto.ThreeFixedDtos;
import com.zhineng.platform.basicinfo.threefixedplan.service.ThreeFixedException;
import com.zhineng.platform.basicinfo.threefixedplan.service.ThreeFixedPlanService;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/basic-info")
public class ThreeFixedPlanController {
    private static final int MAX_BATCH_FILES = 20;
    private static final long MAX_BATCH_SIZE = 50L * 1024 * 1024;
    private final ThreeFixedPlanService service;
    private final ObjectMapper mapper;

    public ThreeFixedPlanController(ThreeFixedPlanService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/three-fixed-plans")
    public ThreeFixedDtos.Page page(
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String year,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.page(orgUnitId, keyword, status, year, page, size);
    }

    @GetMapping("/three-fixed-plans/{id}")
    public Map<String, Object> plan(@PathVariable long id) {
        return service.plan(id);
    }

    @GetMapping("/three-fixed-plans/{id}/versions")
    public List<Map<String, Object>> versions(@PathVariable long id) {
        return service.versions(id);
    }

    @GetMapping("/three-fixed-plan-versions/{id}")
    public Map<String, Object> version(@PathVariable long id) {
        return service.version(id);
    }

    @PostMapping("/three-fixed-plans/manual")
    public Map<String, Object> manual(@RequestBody ThreeFixedDtos.ManualRequest request) {
        return service.createManual(request);
    }

    @PostMapping(value = "/three-fixed-plans/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestParam long orgUnitId,
            @RequestParam(required = false) String planName,
            @RequestPart("file") MultipartFile file
    ) {
        return service.upload(orgUnitId, planName, file, "SINGLE_UPLOAD");
    }

    @PostMapping(value = "/three-fixed-plans/batch-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<ThreeFixedDtos.BatchResult> batchUpload(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("items") String itemsJson
    ) throws IOException {
        if (files.isEmpty() || files.size() > MAX_BATCH_FILES) {
            throw new ThreeFixedException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_BATCH_COUNT", "单次批量必须为1至20个文件");
        }
        long total = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > MAX_BATCH_SIZE) {
            throw new ThreeFixedException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "BATCH_TOO_LARGE", "批量文件总大小不能超过50MB");
        }
        List<ThreeFixedDtos.BatchItem> items = mapper.readValue(
                itemsJson, new TypeReference<>() {});
        if (items.size() != files.size()) {
            throw new ThreeFixedException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "BATCH_BINDING_MISMATCH", "文件与机构绑定数量不一致");
        }
        List<ThreeFixedDtos.BatchResult> results = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            ThreeFixedDtos.BatchItem item = items.get(index);
            try {
                Map<String, Object> version = service.upload(
                        item.orgUnitId(), item.planName(), file, "BATCH_UPLOAD");
                results.add(new ThreeFixedDtos.BatchResult(
                        index, file.getOriginalFilename(), true,
                        ((Number) version.get("id")).longValue(), "导入成功"));
            } catch (RuntimeException exception) {
                results.add(new ThreeFixedDtos.BatchResult(
                        index, file.getOriginalFilename(), false, null, exception.getMessage()));
            }
        }
        return results;
    }

    @PutMapping("/three-fixed-plan-versions/{id}")
    public Map<String, Object> update(
            @PathVariable long id, @RequestBody ThreeFixedDtos.UpdateRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/three-fixed-plan-versions/{id}/reparse")
    public Map<String, Object> reparse(
            @PathVariable long id, @RequestBody ThreeFixedDtos.SubmitRequest request
    ) {
        return service.reparse(id, request);
    }

    @PostMapping("/three-fixed-plan-versions/{id}/submit")
    public Map<String, Object> submit(
            @PathVariable long id, @RequestBody ThreeFixedDtos.SubmitRequest request
    ) {
        return service.submit(id, request);
    }

    @PostMapping("/three-fixed-plan-versions/{id}/review")
    public Map<String, Object> review(
            @PathVariable long id, @RequestBody ThreeFixedDtos.ReviewRequest request
    ) {
        return service.review(id, request);
    }

    @GetMapping("/three-fixed-field-mappings")
    public List<Map<String, Object>> mappings() {
        return service.mappings();
    }

    @PostMapping("/three-fixed-field-mappings")
    public Map<String, Object> createMapping(
            @RequestBody ThreeFixedDtos.MappingRequest request
    ) {
        return service.createMapping(request);
    }

    @PutMapping("/three-fixed-field-mappings/{id}")
    public Map<String, Object> updateMapping(
            @PathVariable long id, @RequestBody ThreeFixedDtos.MappingRequest request
    ) {
        return service.updateMapping(id, request);
    }

    @PutMapping("/three-fixed-field-mappings/{id}/status")
    public Map<String, Object> updateMappingStatus(
            @PathVariable long id, @RequestBody ThreeFixedDtos.MappingStatusRequest request
    ) {
        return service.updateMappingStatus(id, request);
    }

    @GetMapping("/three-fixed-attachments/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable long id) throws IOException {
        ThreeFixedPlanService.Download download = service.download(id);
        String contentType = download.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : download.contentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(Files.size(download.path()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.originalName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .body(new InputStreamResource(Files.newInputStream(download.path())));
    }
}
