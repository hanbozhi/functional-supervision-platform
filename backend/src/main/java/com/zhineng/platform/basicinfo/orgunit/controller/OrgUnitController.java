package com.zhineng.platform.basicinfo.orgunit.controller;

import com.zhineng.platform.basicinfo.orgunit.dto.OrgUnitDtos;
import com.zhineng.platform.basicinfo.orgunit.service.OrgUnitService;
import java.util.List;
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
@RequestMapping("/api/basic-info/org-units")
public class OrgUnitController {
    private final OrgUnitService service;

    public OrgUnitController(OrgUnitService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    public List<OrgUnitDtos.TreeNode> tree(
            @RequestParam(defaultValue = "true") boolean includeInactive
    ) {
        return service.tree(includeInactive);
    }

    @GetMapping
    public OrgUnitDtos.Page page(
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "SUBTREE") String scope,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String unitType,
            @RequestParam(required = false) String unitLevel,
            @RequestParam(required = false) String organizationNature,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.page(parentId, scope, keyword, unitType, unitLevel,
                organizationNature, status, verificationStatus, page, size);
    }

    @GetMapping("/stats")
    public OrgUnitDtos.Stats stats(
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "SUBTREE") String scope
    ) {
        return service.stats(parentId, scope);
    }

    @GetMapping("/options")
    public OrgUnitDtos.Options options() {
        return service.options();
    }

    @GetMapping("/{id}")
    public OrgUnitDtos.Detail detail(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrgUnitDtos.Detail create(@RequestBody OrgUnitDtos.SaveRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public OrgUnitDtos.Detail update(
            @PathVariable long id,
            @RequestBody OrgUnitDtos.SaveRequest request
    ) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/status")
    public OrgUnitDtos.Detail updateStatus(
            @PathVariable long id,
            @RequestBody OrgUnitDtos.StatusRequest request
    ) {
        return service.updateStatus(id, request);
    }

    @PostMapping("/{id}/verifications")
    public OrgUnitDtos.Detail verify(
            @PathVariable long id,
            @RequestBody OrgUnitDtos.VerificationRequest request
    ) {
        return service.verify(id, request);
    }

    @GetMapping("/{id}/verifications")
    public List<OrgUnitDtos.Verification> verifications(@PathVariable long id) {
        return service.verifications(id);
    }
}
