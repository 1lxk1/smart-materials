package com.smartmaterials.service;

import com.smartmaterials.common.BizException;
import com.smartmaterials.config.AppProperties;
import com.smartmaterials.support.AppTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 附件本地存储：损耗照片、报废凭证等。
 *
 * <p>文件落到 {@code app.upload.dir}（默认 ./uploads），按
 * {@code 类别/年月/uuid.ext} 分目录，返回可直接访问的 {@code /uploads/...} 相对地址，
 * 该地址会写进 {@code stock_transaction.attachments} 这个 jsonb 数组。</p>
 *
 * <p>生产提示：Render 免费实例的文件系统是临时的，重启即丢；
 * 长期方案是把 UPLOAD_DIR 指向持久盘或改成 S3/OSS，接口契约不变。</p>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final Set<String> ALLOWED_EXT =
            Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp", "pdf");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path root;
    private final long maxSizeBytes;

    public FileStorageService(AppProperties properties) {
        this.root = Paths.get(properties.getUpload().getDir()).toAbsolutePath().normalize();
        this.maxSizeBytes = properties.getUpload().getMaxSizeBytes();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建附件目录：" + root, e);
        }
        log.info("附件存储目录：{}（单文件上限 {} KB）", root, maxSizeBytes / 1024);
    }

    /** 存储根目录，静态资源映射使用。 */
    public Path getRoot() {
        return root;
    }

    /**
     * 保存上传文件。
     *
     * @param file     上传的文件
     * @param category 业务类别（scrap / return / avatar ...），只保留安全字符
     */
    public StoredFile store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择要上传的文件");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BizException(413, "文件超过上限 " + (maxSizeBytes / 1024 / 1024) + "MB");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = extensionOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(400, "不支持的文件类型：" + (ext.isEmpty() ? "未知" : ext)
                    + "，仅允许 " + String.join("/", ALLOWED_EXT));
        }

        String safeCategory = StringUtils.hasText(category)
                ? category.replaceAll("[^a-zA-Z0-9_-]", "")
                : "misc";
        if (safeCategory.isEmpty()) {
            safeCategory = "misc";
        }
        String month = LocalDate.now(AppTime.ZONE).format(MONTH);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        Path dir = root.resolve(safeCategory).resolve(month);
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(400, "非法的文件路径");
        }
        try {
            Files.createDirectories(dir);
            file.transferTo(target);
        } catch (IOException e) {
            log.error("附件写入失败：{}", target, e);
            throw new BizException(500, "附件保存失败，请稍后重试");
        }
        String url = "/uploads/" + safeCategory + "/" + month + "/" + fileName;
        log.info("附件已保存：{}（{} B）-> {}", original, file.getSize(), url);
        return new StoredFile(url, original, file.getSize());
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 存储结果：可直接给前端展示 / 写库的地址。 */
    public record StoredFile(String url, String originalName, long size) {
    }
}
