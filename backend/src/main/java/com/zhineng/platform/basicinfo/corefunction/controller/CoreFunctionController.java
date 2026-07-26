package com.zhineng.platform.basicinfo.corefunction.controller;

import com.zhineng.platform.basicinfo.corefunction.dto.CoreFunctionDtos;
import com.zhineng.platform.basicinfo.corefunction.service.CoreFunctionService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic-info/core-functions")
public class CoreFunctionController {
    private final CoreFunctionService service;

    public CoreFunctionController(CoreFunctionService service) {
        this.service = service;
    }

    @GetMapping
    public CoreFunctionDtos.Page functions(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.functions(orgId, keyword, status, page, size);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam long orgId) {
        return service.stats(orgId);
    }

    @GetMapping("/{id}")
    public Map<String, Object> function(@PathVariable long id) {
        return service.function(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createFunction(
            @RequestBody CoreFunctionDtos.FunctionRequest request
    ) {
        return service.createFunction(request);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateFunction(
            @PathVariable long id, @RequestBody CoreFunctionDtos.FunctionRequest request
    ) {
        return service.updateFunction(id, request);
    }

    @PutMapping("/{id}/status")
    public Map<String, Object> functionStatus(
            @PathVariable long id, @RequestBody CoreFunctionDtos.StatusRequest request
    ) {
        return service.functionStatus(id, request);
    }

    @GetMapping("/duties")
    public List<Map<String, Object>> duties(
            @RequestParam(required = false) Long functionId,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String status
    ) {
        return service.duties(functionId, orgId, status);
    }

    @PostMapping("/duties")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createDuty(
            @RequestBody CoreFunctionDtos.DutyRequest request
    ) {
        return service.createDuty(request);
    }

    @PutMapping("/duties/{id}")
    public Map<String, Object> updateDuty(
            @PathVariable long id, @RequestBody CoreFunctionDtos.DutyRequest request
    ) {
        return service.updateDuty(id, request);
    }

    @PutMapping("/duties/{id}/status")
    public Map<String, Object> dutyStatus(
            @PathVariable long id, @RequestBody CoreFunctionDtos.StatusRequest request
    ) {
        return service.dutyStatus(id, request);
    }

    @GetMapping("/org-units/{orgId}/duty-import-preview")
    public Map<String, Object> dutyPreview(@PathVariable long orgId) {
        return service.dutyPreview(orgId);
    }

    @PostMapping("/org-units/{orgId}/duty-imports")
    public List<Map<String, Object>> importDuties(
            @PathVariable long orgId,
            @RequestBody CoreFunctionDtos.DutyImportRequest request
    ) {
        return service.importDuties(orgId, request);
    }

    @GetMapping("/org-units/{orgId}/rights-mappings")
    public Map<String, Object> mappings(@PathVariable long orgId) {
        return service.mappings(orgId);
    }

    @PutMapping("/org-units/{orgId}/rights-mappings")
    public Map<String, Object> saveMappings(
            @PathVariable long orgId,
            @RequestBody CoreFunctionDtos.MappingRequest request
    ) {
        return service.saveMappings(orgId, request);
    }

    @PostMapping("/org-units/{orgId}/rights-mappings/auto")
    public Map<String, Object> autoMappings(@PathVariable long orgId) {
        return service.autoMappings(orgId);
    }

    @GetMapping("/org-units/{orgId}/match-runs")
    public List<Map<String, Object>> runs(@PathVariable long orgId) {
        return service.runs(orgId);
    }

    @GetMapping("/org-units/{orgId}/rights-items")
    public List<Map<String, Object>> rightsItems(@PathVariable long orgId) {
        return service.rightsItems(orgId);
    }

    @PostMapping("/org-units/{orgId}/match-runs")
    public Map<String, Object> runMatch(
            @PathVariable long orgId,
            @RequestBody(required = false) CoreFunctionDtos.MatchRequest request
    ) {
        return service.runMatch(orgId, request);
    }

    @GetMapping("/match-runs/{runId}/results")
    public List<Map<String, Object>> results(
            @PathVariable long runId,
            @RequestParam(required = false) String resultType,
            @RequestParam(required = false) String reviewStatus
    ) {
        return service.results(runId, resultType, reviewStatus);
    }

    @PutMapping("/match-results/{resultId}/review")
    public Map<String, Object> review(
            @PathVariable long resultId,
            @RequestBody CoreFunctionDtos.ReviewRequest request
    ) {
        return service.review(resultId, request);
    }

    @PostMapping("/match-runs/{runId}/manual-results")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> manualResult(
            @PathVariable long runId,
            @RequestBody CoreFunctionDtos.ReviewRequest request
    ) {
        return service.createManualResult(runId, request);
    }
}
