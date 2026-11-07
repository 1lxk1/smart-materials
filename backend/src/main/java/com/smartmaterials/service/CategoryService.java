package com.smartmaterials.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmaterials.common.BizException;
import com.smartmaterials.dto.CategoryRequest;
import com.smartmaterials.entity.MaterialCategory;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.mapper.MaterialCategoryMapper;
import com.smartmaterials.support.AppTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 材料类目树（支持四级：大类 → 子类 → 品牌 → 型号）。 */
@Service
public class CategoryService {

    private final MaterialCategoryMapper categoryMapper;

    public CategoryService(MaterialCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<MaterialCategory> tree() {
        List<MaterialCategory> all = categoryMapper.selectList(Wrappers.lambdaQuery(MaterialCategory.class)
                .orderByAsc(MaterialCategory::getLevel)
                .orderByAsc(MaterialCategory::getSortNo)
                .orderByAsc(MaterialCategory::getId));

        Map<Long, MaterialCategory> index = new LinkedHashMap<>();
        for (MaterialCategory category : all) {
            category.setChildren(new ArrayList<>());
            index.put(category.getId(), category);
        }

        List<MaterialCategory> roots = new ArrayList<>();
        for (MaterialCategory category : all) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                MaterialCategory parent = index.get(category.getParentId());
                if (parent == null) {
                    roots.add(category);
                } else {
                    parent.getChildren().add(category);
                }
            }
        }
        return roots;
    }

    public List<MaterialCategory> flat() {
        return categoryMapper.selectList(Wrappers.lambdaQuery(MaterialCategory.class)
                .orderByAsc(MaterialCategory::getLevel)
                .orderByAsc(MaterialCategory::getSortNo));
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialCategory create(CategoryRequest request, SysUser operator) {
        MaterialCategory category = new MaterialCategory();
        category.setParentId(request.getParentId());
        category.setName(request.getName());
        category.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit() : "个");
        category.setSafeStock(request.getSafeStock() == null ? BigDecimal.ZERO : request.getSafeStock());
        category.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        category.setCreateBy(operator == null ? "system" : operator.getLdapUid());
        category.setCreateTime(LocalDateTime.now(AppTime.ZONE));
        category.setUpdateTime(LocalDateTime.now(AppTime.ZONE));
        category.setExtra(new LinkedHashMap<>());

        int level = 1;
        if (request.getParentId() != null) {
            MaterialCategory parent = categoryMapper.selectById(request.getParentId());
            if (parent == null) {
                throw BizException.notFound("父级类目不存在");
            }
            level = (parent.getLevel() == null ? 1 : parent.getLevel()) + 1;
            if (level > 4) {
                throw new BizException("类目最多四级（大类 → 子类 → 品牌 → 型号）");
            }
        }
        category.setLevel(level);
        category.setCode(StringUtils.hasText(request.getCode())
                ? request.getCode().trim().toUpperCase()
                : "C" + System.currentTimeMillis());

        Long exists = categoryMapper.selectCount(Wrappers.lambdaQuery(MaterialCategory.class)
                .eq(MaterialCategory::getCode, category.getCode()));
        if (exists != null && exists > 0) {
            throw new BizException(409, "类目编码已存在：" + category.getCode());
        }

        categoryMapper.insert(category);
        return category;
    }

    /** 类目 id → 完整路径（化学试剂 / 酸碱类 / 国药集团 / 盐酸 AR）。 */
    public Map<Long, String> pathIndex() {
        List<MaterialCategory> all = flat();
        Map<Long, MaterialCategory> index = new LinkedHashMap<>();
        for (MaterialCategory category : all) {
            index.put(category.getId(), category);
        }
        Map<Long, String> paths = new LinkedHashMap<>();
        for (MaterialCategory category : all) {
            List<String> names = new ArrayList<>();
            MaterialCategory cursor = category;
            int guard = 0;
            while (cursor != null && guard++ < 8) {
                names.add(0, cursor.getName());
                cursor = cursor.getParentId() == null ? null : index.get(cursor.getParentId());
            }
            paths.put(category.getId(), String.join(" / ", names));
        }
        return paths;
    }
}
