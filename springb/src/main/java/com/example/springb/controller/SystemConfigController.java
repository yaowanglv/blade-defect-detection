package com.example.springb.controller;

import com.example.springb.annotation.RequirePermission;
import com.example.springb.common.Result;
import com.example.springb.entity.SystemConfig;
import com.example.springb.entity.UiBrandingConfig;
import com.example.springb.service.SystemConfigService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/config")
@RequirePermission("system:config")
public class SystemConfigController {

    @Resource
    private SystemConfigService systemConfigService;

    @Value("${file.access.url:http://localhost:1234/files/}")
    private String fileAccessUrl;

    @Value("${file.upload.path:uploads/}")
    private String uploadPath;

    @GetMapping("/getByGroup")
    public Result getByGroup(@RequestParam("group") String group) {
        return Result.success(systemConfigService.getByGroup(group));
    }

    @GetMapping("/list")
    public Result list() {
        return Result.success(systemConfigService.getAll());
    }

    @GetMapping("/uiBranding")
    public Result getUiBranding() {
        return Result.success(systemConfigService.getUiBrandingConfig());
    }

    @PutMapping("/update")
    public Result update(@RequestBody SystemConfig config) {
        systemConfigService.saveConfig(config);
        return Result.success();
    }

    @PutMapping("/batchUpdate")
    public Result batchUpdate(@RequestBody List<SystemConfig> configs) {
        systemConfigService.batchUpdate(configs);
        return Result.success();
    }

    @PutMapping("/uiBranding")
    public Result updateUiBranding(@RequestBody UiBrandingConfig config) {
        systemConfigService.saveUiBrandingConfig(config);
        return Result.success();
    }

    @PostMapping("/uploadLogo")
    public Result uploadLogo(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return Result.error("未选择Logo文件");
            }

            String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            String ext = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < originalName.length() - 1) {
                ext = originalName.substring(dotIndex + 1).toLowerCase();
            }
            if (!List.of("png", "jpg", "jpeg", "gif", "webp", "svg", "ico").contains(ext)) {
                return Result.error("Logo仅支持 png/jpg/jpeg/gif/webp/svg/ico");
            }

            File uploadRoot = new File(uploadPath);
            if (!uploadRoot.isAbsolute()) {
                uploadRoot = new File(System.getProperty("user.dir"), uploadPath);
            }

            String relativeDir = "branding";
            File targetDir = new File(uploadRoot, relativeDir);
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                return Result.error("创建Logo目录失败");
            }

            String fileName = UUID.randomUUID() + "." + ext;
            File targetFile = new File(targetDir, fileName);
            file.transferTo(targetFile);

            return Result.success(Map.of(
                    "logoUrl", fileAccessUrl + relativeDir + "/" + fileName,
                    "relativePath", relativeDir + "/" + fileName
            ));
        } catch (Exception e) {
            return Result.error("Logo上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/reset")
    public Result reset(@RequestParam("key") String key, @RequestParam("group") String group) {
        systemConfigService.resetToDefault(key, group);
        return Result.success();
    }

    @GetMapping("/listModels")
    public Result listModels(@RequestParam("group") String group,
                             @RequestParam(value = "rootPath", required = false) String rootPath,
                             @RequestParam(value = "includeHidden", defaultValue = "false") Boolean includeHidden) {
        String path = (rootPath == null || rootPath.trim().isEmpty())
                ? systemConfigService.getModelRootPath(group)
                : rootPath;
        return Result.success(systemConfigService.scanModels(group, path, Boolean.TRUE.equals(includeHidden)));
    }

    @PutMapping("/modelAliases")
    public Result updateModelAliases(@RequestParam("group") String group,
                                     @RequestBody Map<String, String> aliases) {
        systemConfigService.saveModelAliases(group, aliases);
        return Result.success();
    }

    @PutMapping("/hiddenModels")
    public Result updateHiddenModels(@RequestParam("group") String group,
                                     @RequestBody List<String> hiddenModels) {
        systemConfigService.saveHiddenModels(group, hiddenModels);
        return Result.success();
    }
}
