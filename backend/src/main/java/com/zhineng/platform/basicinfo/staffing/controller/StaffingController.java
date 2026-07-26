package com.zhineng.platform.basicinfo.staffing.controller;

import com.zhineng.platform.basicinfo.staffing.dto.StaffingDtos;
import com.zhineng.platform.basicinfo.staffing.service.StaffingService;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
@RequestMapping("/api/basic-info/staffing-ledgers")
public class StaffingController {
    private final StaffingService service;

    public StaffingController(StaffingService service) {
        this.service = service;
    }

    @GetMapping
    public StaffingDtos.Page page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String maintenanceStatus,
            @RequestParam(required = false) String anomalyStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.page(keyword, maintenanceStatus, anomalyStatus, page, size);
    }

    @GetMapping("/stats")
    public StaffingDtos.Stats stats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String maintenanceStatus,
            @RequestParam(required = false) String anomalyStatus
    ) {
        return service.stats(keyword, maintenanceStatus, anomalyStatus);
    }

    @GetMapping("/{id}")
    public StaffingDtos.ListItem detail(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffingDtos.ListItem create(@RequestBody StaffingDtos.SaveRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public StaffingDtos.ListItem update(
            @PathVariable long id, @RequestBody StaffingDtos.SaveRequest request
    ) {
        return service.update(id, request);
    }

    @PutMapping("/batch")
    public List<StaffingDtos.ListItem> batch(@RequestBody StaffingDtos.BatchRequest request) {
        return service.batch(request);
    }

    @GetMapping("/{id}/changes")
    public StaffingDtos.ChangePage changes(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.changes(id, page, size);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("编制人员台账导入模板.xlsx", StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        return new ResponseEntity<>(service.template(), headers, HttpStatus.OK);
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StaffingDtos.ImportResult importFile(@RequestPart("file") MultipartFile file) {
        return service.importFile(file);
    }

    @GetMapping("/imports/{batchId}")
    public StaffingDtos.ImportResult importResult(@PathVariable long batchId) {
        return service.importResult(batchId);
    }
}
