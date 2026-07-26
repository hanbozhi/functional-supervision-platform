package com.zhineng.platform.controller;

import com.zhineng.platform.dto.OptionResponse;
import com.zhineng.platform.dto.PageResponse;
import com.zhineng.platform.dto.RightsItemResponse;
import com.zhineng.platform.dto.RightsStatsResponse;
import com.zhineng.platform.service.RightsItemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic-info/rights-items")
public class RightsItemController {

    private final RightsItemService service;

    public RightsItemController(RightsItemService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<RightsItemResponse> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String powerType,
            @RequestParam(required = false) String sourceFile
    ) {
        return service.page(page, size, keyword, department, powerType, sourceFile);
    }

    @GetMapping("/{id}")
    public RightsItemResponse detail(@PathVariable long id) {
        return service.detail(id);
    }

    @GetMapping("/options")
    public OptionResponse options() {
        return service.options();
    }

    @GetMapping("/stats")
    public RightsStatsResponse stats() {
        return service.stats();
    }
}
