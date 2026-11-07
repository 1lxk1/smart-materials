package com.smartmaterials.controller;

import com.smartmaterials.dto.CategoryRequest;
import com.smartmaterials.entity.MaterialCategory;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 材料类目。 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 类目树（含 children）。 */
    @GetMapping
    public List<MaterialCategory> tree() {
        return categoryService.tree();
    }

    /** 扁平列表，下拉框用。 */
    @GetMapping("/flat")
    public List<MaterialCategory> flat() {
        return categoryService.flat();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public MaterialCategory create(@Valid @RequestBody CategoryRequest request,
                                   @AuthenticationPrincipal SysUser operator) {
        return categoryService.create(request, operator);
    }
}
