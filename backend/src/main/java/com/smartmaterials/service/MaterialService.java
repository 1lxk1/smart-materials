package com.smartmaterials.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmaterials.common.BizException;
import com.smartmaterials.dto.MaterialRequest;
import com.smartmaterials.entity.MaterialCategory;
import com.smartmaterials.entity.MaterialItem;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.mapper.MaterialCategoryMapper;
import com.smartmaterials.mapper.MaterialItemMapper;
import com.smartmaterials.support.AppTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 材料台账。 */
@Service
public class MaterialService {

    private final MaterialItemMapper itemMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final CategoryService categoryService;

    public MaterialService(MaterialItemMapper itemMapper,
                           MaterialCategoryMapper categoryMapper,
                           CategoryService categoryService) {
        this.itemMapper = itemMapper;
        this.categoryMapper = categoryMapper;
        this.categoryService = categoryService;
    }

    public List<MaterialItem> list(String keyword) {
        List<MaterialItem> items = itemMapper.selectList(Wrappers.lambdaQuery(MaterialItem.class)
                .orderByAsc(MaterialItem::getId));
        Map<Long, String> paths = categoryService.pathIndex();

        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return items.stream()
                .peek(item -> item.setCategoryPath(paths.getOrDefault(item.getCategoryId(), "")))
                .filter(item -> kw.isEmpty() || matches(item, kw))
                .toList();
    }

    public MaterialItem get(Long id) {
        MaterialItem item = itemMapper.selectById(id);
        if (item == null) {
            throw BizException.notFound("材料不存在");
        }
        item.setCategoryPath(categoryService.pathIndex().getOrDefault(item.getCategoryId(), ""));
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialItem create(MaterialRequest request, SysUser operator) {
        MaterialCategory category = categoryMapper.selectById(request.getCategoryId());
        if (category == null) {
            throw BizException.notFound("类目不存在");
        }
        Long exists = itemMapper.selectCount(Wrappers.lambdaQuery(MaterialItem.class)
                .eq(MaterialItem::getCode, request.getCode().trim()));
        if (exists != null && exists > 0) {
            throw new BizException(409, "材料编码已存在：" + request.getCode());
        }

        MaterialItem item = new MaterialItem();
        item.setCategoryId(request.getCategoryId());
        item.setCode(request.getCode().trim().toUpperCase(Locale.ROOT));
        item.setBrand(request.getBrand());
        item.setModel(request.getModel());
        item.setSpec(request.getSpec());
        item.setUnitPrice(request.getUnitPrice() == null ? BigDecimal.ZERO : request.getUnitPrice());
        item.setCurrency(StringUtils.hasText(request.getCurrency()) ? request.getCurrency() : "CNY");
        item.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit() : category.getUnit());
        item.setSafeStock(request.getSafeStock() == null ? BigDecimal.ZERO : request.getSafeStock());
        item.setStockQty(BigDecimal.ZERO);
        item.setSpecs(request.getSpecs() == null ? new LinkedHashMap<>() : request.getSpecs());
        item.setStatus("ACTIVE");
        item.setCreateTime(LocalDateTime.now(AppTime.ZONE));
        item.setUpdateTime(LocalDateTime.now(AppTime.ZONE));
        itemMapper.insert(item);
        item.setCategoryPath(categoryService.pathIndex().getOrDefault(item.getCategoryId(), ""));
        return item;
    }

    private boolean matches(MaterialItem item, String kw) {
        return contains(item.getBrand(), kw)
                || contains(item.getModel(), kw)
                || contains(item.getSpec(), kw)
                || contains(item.getCode(), kw)
                || contains(item.getCategoryPath(), kw);
    }

    private boolean contains(String value, String kw) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }
}
