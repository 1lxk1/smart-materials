package com.smartmaterials.controller;

import com.smartmaterials.service.FileStorageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/** 附件上传：前端先传到本接口拿到 url，再把 url 数组随业务请求提交。 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final FileStorageService storageService;

    public AttachmentController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 上传单个附件。
     *
     * @param file     图片 / PDF
     * @param category 业务类别，决定落地子目录，默认 scrap（报废损耗）
     * @return {url, name, size}，url 形如 /uploads/scrap/202611/xxx.jpg
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "scrap") String category) {
        FileStorageService.StoredFile stored = storageService.store(file, category);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", stored.url());
        body.put("name", stored.originalName());
        body.put("size", stored.size());
        return body;
    }
}
