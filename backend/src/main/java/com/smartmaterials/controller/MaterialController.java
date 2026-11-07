package com.smartmaterials.controller;

import com.smartmaterials.dto.MaterialRequest;
import com.smartmaterials.entity.MaterialItem;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.service.MaterialService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 材料台账。 */
@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping
    public List<MaterialItem> list(@RequestParam(required = false) String keyword) {
        return materialService.list(keyword);
    }

    @GetMapping("/{id}")
    public MaterialItem get(@PathVariable Long id) {
        return materialService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER', 'LAB')")
    public MaterialItem create(@Valid @RequestBody MaterialRequest request,
                              @AuthenticationPrincipal SysUser operator) {
        return materialService.create(request, operator);
    }
}
