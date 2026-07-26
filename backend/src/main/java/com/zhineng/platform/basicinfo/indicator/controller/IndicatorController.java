package com.zhineng.platform.basicinfo.indicator.controller;

import com.zhineng.platform.basicinfo.indicator.dto.IndicatorDtos;
import com.zhineng.platform.basicinfo.indicator.service.IndicatorService;
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
@RequestMapping("/api/basic-info")
public class IndicatorController {
    private final IndicatorService service;

    public IndicatorController(IndicatorService service) {
        this.service = service;
    }

    @GetMapping("/indicator-systems")
    public List<Map<String, Object>> systems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status
    ) {
        return service.systems(keyword, year, status);
    }

    @PostMapping("/indicator-systems")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSystem(
            @RequestBody IndicatorDtos.SystemRequest request
    ) {
        return service.createSystem(request);
    }

    @GetMapping("/indicator-systems/{id}")
    public Map<String, Object> system(@PathVariable long id) {
        return service.system(id);
    }

    @GetMapping("/indicator-versions")
    public List<Map<String, Object>> versions(
            @RequestParam(required = false) Long systemId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status
    ) {
        return service.versions(systemId, year, status);
    }

    @GetMapping("/indicator-versions/{id}")
    public Map<String, Object> version(@PathVariable long id) {
        return service.version(id);
    }

    @GetMapping("/indicator-versions/{id}/tree")
    public List<Map<String, Object>> tree(@PathVariable long id) {
        return service.tree(id);
    }

    @PostMapping("/indicator-versions/{id}/publish")
    public Map<String, Object> publish(
            @PathVariable long id,
            @RequestBody IndicatorDtos.StatusRequest request
    ) {
        return service.publish(id, request.rowVersion());
    }

    @PostMapping("/indicator-versions/{id}/archive")
    public Map<String, Object> archive(
            @PathVariable long id,
            @RequestBody IndicatorDtos.StatusRequest request
    ) {
        return service.archiveVersion(id, request.rowVersion());
    }

    @PostMapping("/indicator-versions/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> copyVersion(
            @PathVariable long id,
            @RequestBody IndicatorDtos.CopyVersionRequest request
    ) {
        return service.copyVersion(id, request);
    }

    @PostMapping("/indicator-items")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createItem(@RequestBody IndicatorDtos.ItemRequest request) {
        return service.createItem(request);
    }

    @PutMapping("/indicator-items/{id}")
    public Map<String, Object> updateItem(
            @PathVariable long id, @RequestBody IndicatorDtos.ItemRequest request
    ) {
        return service.updateItem(id, request);
    }

    @PutMapping("/indicator-items/{id}/status")
    public Map<String, Object> itemStatus(
            @PathVariable long id, @RequestBody IndicatorDtos.StatusRequest request
    ) {
        return service.itemStatus(id, request);
    }

    @GetMapping("/indicator-rules")
    public List<Map<String, Object>> rules(
            @RequestParam(required = false) Long versionId,
            @RequestParam(required = false) Long indicatorId
    ) {
        return service.rules(versionId, indicatorId);
    }

    @PostMapping("/indicator-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRule(@RequestBody IndicatorDtos.RuleRequest request) {
        return service.createRule(request);
    }

    @PutMapping("/indicator-rules/{id}")
    public Map<String, Object> updateRule(
            @PathVariable long id, @RequestBody IndicatorDtos.RuleRequest request
    ) {
        return service.updateRule(id, request);
    }

    @PutMapping("/indicator-rules/{id}/status")
    public Map<String, Object> ruleStatus(
            @PathVariable long id, @RequestBody IndicatorDtos.StatusRequest request
    ) {
        return service.ruleStatus(id, request);
    }

    @GetMapping("/indicator-templates")
    public List<Map<String, Object>> templates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orgType,
            @RequestParam(required = false) String status
    ) {
        return service.templates(keyword, orgType, status);
    }

    @GetMapping("/indicator-templates/{id}")
    public Map<String, Object> template(@PathVariable long id) {
        return service.template(id);
    }

    @PostMapping("/indicator-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTemplate(
            @RequestBody IndicatorDtos.TemplateRequest request
    ) {
        return service.createTemplate(request);
    }

    @PostMapping("/indicator-templates/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> copyTemplate(
            @PathVariable long id,
            @RequestBody IndicatorDtos.TemplateCopyRequest request
    ) {
        return service.copyTemplate(id, request);
    }

    @PutMapping("/indicator-templates/{id}/status")
    public Map<String, Object> templateStatus(
            @PathVariable long id,
            @RequestBody IndicatorDtos.StatusRequest request
    ) {
        return service.templateStatus(id, request);
    }

    @PostMapping("/indicator-templates/{id}/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> initialize(
            @PathVariable long id,
            @RequestBody IndicatorDtos.TemplateInitializeRequest request
    ) {
        return service.initializeFromTemplate(id, request);
    }
}
