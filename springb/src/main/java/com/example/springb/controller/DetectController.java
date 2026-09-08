package com.example.springb.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.example.springb.annotation.RequirePermission;
import com.example.springb.common.Result;
import com.example.springb.entity.Detect;
import com.example.springb.entity.DetectionLog;
import com.example.springb.service.AIService;
import com.example.springb.service.DetectService;
import com.example.springb.service.DetectionLogService;
import com.example.springb.service.SystemConfigService;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/detect")
public class DetectController {

    private static final Logger log = LoggerFactory.getLogger(DetectController.class);
    // 模型根目录：指向 V11-dmt 项目的 race-blade 文件夹
    private static final String BLADE_MODEL_ROOT = "D:/algorithms/V11-dmt/runs/race-blade";
    private static final String PAIRING_MODE_SPLIT_DIRS = "split_dirs";
    private static final String PAIRING_MODE_SAME_DIR_SUFFIX = "same_dir_suffix";
    private static final String DEFAULT_DATASET_ROOT = "D:/opendataset/Wind-Turbine";

    @Resource
    private DetectService detectService;

    @Resource
    private DetectionLogService detectionLogService;

    @Resource
    private AIService aiService;

    @Resource
    private SystemConfigService systemConfigService;

    // 文件上传路径配置
    @Value("${file.upload.path:uploads/}")
    private String uploadPath;

    @Value("${file.access.url:http://localhost:1234/files/}")
    private String fileAccessUrl;

    // Python检测服务地址
    @Value("${python.detect.url:http://localhost:6522}")
    private String pythonDetectUrl;

    // ==================== 基础CRUD接口 ====================

    @GetMapping("/health")
    @RequirePermission("detect:read")
    public Result health() {
        return Result.success("Detect服务运行正常");
    }

    @GetMapping("/pythonHealth")
    @RequirePermission("detect:read")
    public Result pythonHealth() {
        try {
            String url = getPythonDetectUrl() + "/health";
            HttpResponse response = HttpRequest.get(url)
                    .timeout(5000)
                    .execute();

            if (response.getStatus() == 200) {
                return Result.success(JSONUtil.parseObj(response.body()));
            } else {
                return Result.error("Python检测服务响应异常: " + response.getStatus());
            }
        } catch (Exception e) {
            log.error("Python检测服务连接失败", e);
            return Result.error("Python检测服务连接失败: " + e.getMessage());
        }
    }

    @GetMapping("/selectPage")
    @RequirePermission("detect:read")
    public Result selectPage(@RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize,
                             Detect detect) {
        PageInfo<Detect> pageInfo = detectService.selectPage(pageNum, pageSize, detect);
        return Result.success(pageInfo);
    }

    @GetMapping("/selectById/{id}")
    @RequirePermission("detect:read")
    public Result selectById(@PathVariable Integer id) {
        Detect detect = detectService.selectById(id);
        return Result.success(detect);
    }

    @DeleteMapping("/delete/{id}")
    @RequirePermission("detect:delete")
    public Result delete(@PathVariable Integer id) {
        detectService.deleteById(id);
        return Result.success();
    }

    /**
     * 读取本地文件并返回图像（用于读取另一模态的红外图像）
     */
    @GetMapping("/readLocalFile")
    @RequirePermission("file:read")
    public void readLocalFile(@RequestParam String path, HttpServletResponse response) {
        try {
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                response.setStatus(404);
                response.getWriter().write("文件不存在");
                return;
            }
            String ext = FileUtil.extName(file.getName()).toLowerCase();
            String contentType;
            switch (ext) {
                case "jpg":
                case "jpeg":
                    contentType = "image/jpeg";
                    break;
                case "png":
                    contentType = "image/png";
                    break;
                case "bmp":
                    contentType = "image/bmp";
                    break;
                case "gif":
                    contentType = "image/gif";
                    break;
                default:
                    contentType = "application/octet-stream";
                    break;
            }
            response.setContentType(contentType);
            response.setHeader("Cache-Control", "public, max-age=86400");
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            fis.transferTo(response.getOutputStream());
            fis.close();
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("读取本地文件失败: {}", path, e);
            try {
                response.setStatus(500);
                response.getWriter().write("读取失败: " + e.getMessage());
            } catch (Exception ignored) {}
        }
    }

    // ==================== 图像上传与检测接口 ====================

    @PostMapping("/upload")
    @RequirePermission("detect:write")
    public Result uploadImage(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "irFile", required = false) MultipartFile irFile,
                              @RequestParam("userId") Integer userId,
                              @RequestParam("userName") String userName,
                              @RequestParam(required = false) String filePath) {
        log.info("收到上传请求: userId={}, userName={}, fileName={}, fileSize={}, filePath={}",
                userId, userName, file.getOriginalFilename(), file.getSize(), filePath);

        try {
            String originalName = file.getOriginalFilename();
            String ext = FileUtil.extName(originalName);
            File rgbSourceFromDataset = null;
            File irSourceFromDataset = null;
            String effectiveFilePath = filePath;

            if (irFile == null || irFile.isEmpty()) {
                if (isIrFileName(originalName)) {
                    rgbSourceFromDataset = findRgbCounterpart(originalName, filePath);
                    if (rgbSourceFromDataset == null) {
                        return Result.error("未找到对应RGB图像，已取消上传，避免将红外图像送入检测服务");
                    }
                    irSourceFromDataset = findIrCounterpart(rgbSourceFromDataset.getName(), rgbSourceFromDataset.getAbsolutePath());
                    effectiveFilePath = rgbSourceFromDataset.getAbsolutePath();
                    originalName = rgbSourceFromDataset.getName();
                    ext = FileUtil.extName(originalName);
                    log.info("IR上传已切换为RGB主图: rgb={}, ir={}", rgbSourceFromDataset.getAbsolutePath(),
                            irSourceFromDataset == null ? file.getOriginalFilename() : irSourceFromDataset.getAbsolutePath());
                } else {
                    irSourceFromDataset = findIrCounterpart(originalName, filePath);
                    if (irSourceFromDataset != null) {
                        log.info("后端自动找到配对IR图像: {}", irSourceFromDataset.getAbsolutePath());
                    } else {
                        log.info("未找到配对IR图像: fileName={}, filePath={}", originalName, filePath);
                    }
                }
            }
            String newFileName = UUID.randomUUID() + "." + ext;

            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String relativePath = "detect/" + dateDir + "/" + newFileName;
            String irRelativePath = null;

            String basePath = System.getProperty("user.dir");
            String fullDir = basePath + "/" + uploadPath + "detect/" + dateDir;
            String fullPath = basePath + "/" + uploadPath + relativePath;

            log.info("文件保存目录: {}", fullDir);
            log.info("文件保存路径: {}", fullPath);

            File dir = new File(fullDir);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                log.info("创建目录: {}, 结果: {}", fullDir, created);
                if (!created) {
                    return Result.error("创建上传目录失败: " + fullDir);
                }
            }

            File destFile = new File(fullPath);
            if (rgbSourceFromDataset != null) {
                FileUtil.copy(rgbSourceFromDataset, destFile, true);
            } else {
                file.transferTo(destFile);
            }
            if (irFile != null && !irFile.isEmpty()) {
                String irOriginalName = irFile.getOriginalFilename();
                String irExt = FileUtil.extName(irOriginalName);
                String irNewFileName = UUID.randomUUID() + "." + irExt;
                irRelativePath = "detect/" + dateDir + "/" + irNewFileName;
                String irFullPath = basePath + "/" + uploadPath + irRelativePath;
                irFile.transferTo(new File(irFullPath));
                log.info("IR file saved: {}", irFullPath);
            } else if (irSourceFromDataset != null) {
                String irExt = FileUtil.extName(irSourceFromDataset.getName());
                String irNewFileName = UUID.randomUUID() + "." + irExt;
                irRelativePath = "detect/" + dateDir + "/" + irNewFileName;
                String irFullPath = basePath + "/" + uploadPath + irRelativePath;
                FileUtil.copy(irSourceFromDataset, new File(irFullPath), true);
                log.info("IR file saved from dataset: {}", irFullPath);
            }
            log.info("文件保存成功: {}", fullPath);

            Detect detect = new Detect();
            detect.setUserId(userId);
            detect.setUserName(userName);
            detect.setOriginalImageName(originalName);
            detect.setOriginalImageUrl(relativePath);
            detect.setOriginalImageSize(rgbSourceFromDataset != null ? rgbSourceFromDataset.length() : file.getSize());
            detect.setOriginalImageFormat(ext);
            detect.setDetectStatus(0);
            detect.setAiStatus(0);
            // 存储原始文件路径（用于查找配对红外图像）
            if ((effectiveFilePath != null && !effectiveFilePath.isEmpty()) || irRelativePath != null) {
                Map<String, Object> remarkMeta = new HashMap<>();
                if (effectiveFilePath != null && !effectiveFilePath.isEmpty()) {
                    remarkMeta.put("filePath", effectiveFilePath);
                }
                if (irRelativePath != null) {
                    remarkMeta.put("irRelativeUrl", irRelativePath);
                }
                detect.setRemark(JSONUtil.toJsonStr(remarkMeta));
            }

            detectService.add(detect);
            log.info("检测记录创建成功: id={}", detect.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("id", detect.getId());
            result.put("originalImageUrl", fileAccessUrl + relativePath);
            result.put("originalImageName", originalName);
            if (irRelativePath != null) {
                result.put("irImageUrl", fileAccessUrl + irRelativePath);
            }

            return Result.success(result);

        } catch (IOException e) {
            log.error("图像上传失败", e);
            return Result.error("图像上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("图像上传异常", e);
            return Result.error("图像上传异常: " + e.getMessage());
        }
    }

    @PostMapping("/startDetect/{id}")
    @RequirePermission("detect:write")
    public Result startDetect(@PathVariable Integer id,
                              @RequestParam(required = false) String ptPath,
                              @RequestParam(required = false) Double conf) {
        if (ptPath == null || ptPath.isEmpty()) {
            ptPath = findDefaultModelPath();
        }
        if (conf == null) {
            conf = systemConfigService.getDoubleValue("default_threshold", "detect", SystemConfigService.DEFAULT_DETECT_THRESHOLD);
        }
        try {
            Detect detect = detectService.selectById(id);
            if (detect == null) {
                return Result.error("检测记录不存在");
            }

            detect.setDetectStatus(1);
            detectService.update(detect);

            log.info("开始检测记录: {}, ptPath={}, conf={}", id, ptPath, conf);

            String basePath = System.getProperty("user.dir");
            String imagePath = basePath + "/" + uploadPath + detect.getOriginalImageUrl();

            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                throw new RuntimeException("图像文件不存在: " + imagePath);
            }

            String url = getPythonDetectUrl() + "/detect";
            log.info("调用Python检测服务: {}", url);

            HttpResponse response = HttpRequest.post(url)
                    .form("file", imageFile)
                    .form("pt_path", ptPath)
                    .form("conf", String.valueOf(conf))
                    .form("mode", "detect")
                    .form("return_image", "true")
                    .form("save_result", "false")
                    .timeout(120000)
                    .execute();

            if (response.getStatus() != 200) {
                throw new RuntimeException("Python服务响应异常: " + response.getStatus());
            }

            String responseBody = response.body();
            log.debug("Python服务响应: {}", responseBody);

            cn.hutool.json.JSONObject jsonResponse = JSONUtil.parseObj(responseBody);
            int code = jsonResponse.getInt("code", -1);

            if (code != 0) {
                String message = jsonResponse.getStr("message", "检测失败");
                throw new RuntimeException(message);
            }

            cn.hutool.json.JSONObject data = jsonResponse.getJSONObject("data");
            cn.hutool.json.JSONObject detectionData = data.getJSONObject("detection");

            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String resultImageBase64 = data.getStr("result_image_base64");
            String resultImagePath = saveBase64Image(basePath, dateDir, "result", id, resultImageBase64, "jpg");

            if (resultImagePath != null) {
                detectionData.set("result_image_url", resultImagePath);
            } else {
                log.warn("Python服务未返回结果图像base64");
            }

            detect.setDetectStatus(2);
            detect.setDetectionData(detectionData.toString());
            if (resultImagePath != null) {
                detect.setResultImageUrl(resultImagePath);
            }
            detectService.update(detect);

            try {
                DetectionLog logEntry = new DetectionLog();
                logEntry.setDetectId(detect.getId());
                logEntry.setUserId(detect.getUserId());
                logEntry.setUserName(detect.getUserName());
                logEntry.setModelName(ptPath);
                logEntry.setConfThreshold(conf);
                logEntry.setAiModel(detect.getAiModel());
                logEntry.setCreateTime(LocalDateTime.now());

                if (detectionData.containsKey("tumor_type")) {
                    logEntry.setTumorType(detectionData.getStr("tumor_type"));
                }
                if (detectionData.containsKey("boxes") && !detectionData.getJSONArray("boxes").isEmpty()) {
                    cn.hutool.json.JSONArray boxes = detectionData.getJSONArray("boxes");
                    double maxConf = 0;
                    for (int i = 0; i < boxes.size(); i++) {
                        cn.hutool.json.JSONObject box = boxes.getJSONObject(i);
                        if (box.containsKey("confidence")) {
                            maxConf = Math.max(maxConf, box.getDouble("confidence"));
                        }
                    }
                    if (maxConf > 0) {
                        logEntry.setConfidence(maxConf);
                    }
                } else if (detectionData.containsKey("confidence")) {
                    logEntry.setConfidence(detectionData.getDouble("confidence"));
                }

                detectionLogService.addLog(logEntry);
                log.info("检测日志记录成功: detectId={}", detect.getId());
            } catch (Exception ex) {
                log.error("记录检测日志失败", ex);
            }

            return Result.success(detect);

        } catch (Exception e) {
            log.error("检测失败", e);
            Detect detect = new Detect();
            detect.setId(id);
            detect.setDetectStatus(3);
            detectService.update(detect);
            return Result.error("检测失败: " + e.getMessage());
        }
    }

    private String saveBase64Image(String basePath, String dateDir, String prefix, Integer id, String base64Image, String extension) {
        if (base64Image == null || base64Image.isEmpty()) {
            return null;
        }

        String resultFileName = prefix + "_" + id + "_" + System.currentTimeMillis() + "." + extension;
        String resultRelativePath = "detect/" + dateDir + "/" + resultFileName;
        String resultFullPath = basePath + "/" + uploadPath + resultRelativePath;

        File resultDir = new File(basePath + "/" + uploadPath + "detect/" + dateDir);
        if (!resultDir.exists()) {
            resultDir.mkdirs();
        }

        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        FileUtil.writeBytes(imageBytes, resultFullPath);
        log.info("结果图像已保存: {}, 大小: {} bytes", resultFullPath, imageBytes.length);
        return resultRelativePath;
    }

    private File findIrCounterpart(String originalName, String filePath) {
        String fileName = normalizeFileName(originalName, filePath);
        if (fileName == null || fileName.isEmpty() || isIrFileName(fileName)) {
            return null;
        }
        List<String> candidates = buildCounterpartCandidates(fileName, filePath, false);
        return firstExistingFile(candidates);
    }

    private File findRgbCounterpart(String originalName, String filePath) {
        String fileName = normalizeFileName(originalName, filePath);
        if (fileName == null || fileName.isEmpty() || !isIrFileName(fileName)) {
            return null;
        }
        List<String> candidates = buildCounterpartCandidates(fileName, filePath, true);
        return firstExistingFile(candidates);
    }

    private List<String> buildCounterpartCandidates(String fileName, String filePath, boolean targetRgb) {
        String normalizedPath = normalizePath(filePath);
        String pairingMode = getImagePairingMode();
        String datasetRoot = normalizePath(systemConfigService.getValue(
                "dataset_root_path",
                "detect",
                DEFAULT_DATASET_ROOT
        ));
        List<String> candidates = new ArrayList<>();

        String directPath = buildDirectCounterpartPath(normalizedPath, pairingMode, targetRgb);
        if (directPath != null && !directPath.equals(normalizedPath)) {
            candidates.add(directPath);
        }

        String counterpartName = buildCounterpartFileName(fileName, pairingMode, targetRgb);
        if (counterpartName == null || datasetRoot == null || datasetRoot.isEmpty()) {
            return candidates;
        }

        if (PAIRING_MODE_SAME_DIR_SUFFIX.equals(pairingMode)) {
            candidates.add(datasetRoot + "/train/" + counterpartName);
            candidates.add(datasetRoot + "/val/" + counterpartName);
            candidates.add(datasetRoot + "/" + counterpartName);
        } else {
            String folder = targetRgb ? "images" : "images_ir";
            candidates.add(datasetRoot + "/" + folder + "/train/" + counterpartName);
            candidates.add(datasetRoot + "/" + folder + "/val/" + counterpartName);
            candidates.add(datasetRoot + "/" + folder + "/" + counterpartName);
        }
        return candidates;
    }

    private String buildDirectCounterpartPath(String filePath, String pairingMode, boolean targetRgb) {
        if (filePath == null || filePath.isEmpty() || !filePath.contains("/")) {
            return null;
        }
        if (PAIRING_MODE_SAME_DIR_SUFFIX.equals(pairingMode)) {
            int slashIndex = filePath.lastIndexOf('/');
            String dir = filePath.substring(0, slashIndex + 1);
            String counterpartName = buildCounterpartFileName(filePath.substring(slashIndex + 1), pairingMode, targetRgb);
            return counterpartName == null ? null : dir + counterpartName;
        }
        if (targetRgb && filePath.contains("/images_ir/")) {
            return filePath.replace("/images_ir/", "/images/");
        }
        if (!targetRgb && filePath.contains("/images/")) {
            return filePath.replace("/images/", "/images_ir/");
        }
        return null;
    }

    private String buildCounterpartFileName(String fileName, String pairingMode, boolean targetRgb) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        if (!PAIRING_MODE_SAME_DIR_SUFFIX.equals(pairingMode)) {
            return fileName;
        }
        int dotIndex = fileName.lastIndexOf('.');
        String base = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String ext = dotIndex > 0 ? fileName.substring(dotIndex) : "";
        if (targetRgb) {
            return base.toLowerCase().endsWith("-ir") ? base.substring(0, base.length() - 3) + ext : fileName;
        }
        return base.toLowerCase().endsWith("-ir") ? fileName : base + "-ir" + ext;
    }

    private File firstExistingFile(List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            File file = new File(candidate);
            if (file.exists() && file.isFile()) {
                return file;
            }
        }
        return null;
    }

    private String normalizeFileName(String originalName, String filePath) {
        String normalizedPath = normalizePath(filePath);
        if (normalizedPath != null && !normalizedPath.isEmpty()) {
            int slashIndex = normalizedPath.lastIndexOf('/');
            return slashIndex >= 0 ? normalizedPath.substring(slashIndex + 1) : normalizedPath;
        }
        return originalName;
    }

    private String normalizePath(String path) {
        return path == null ? null : path.replace("\\", "/").trim();
    }

    private boolean isIrFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String name = normalizeFileName(fileName, fileName);
        int dotIndex = name.lastIndexOf('.');
        String base = dotIndex > 0 ? name.substring(0, dotIndex) : name;
        return base.toLowerCase().endsWith("-ir");
    }

    private String getImagePairingMode() {
        String value = systemConfigService.getValue("image_pairing_mode", "detect", PAIRING_MODE_SPLIT_DIRS);
        return PAIRING_MODE_SAME_DIR_SUFFIX.equals(value) ? PAIRING_MODE_SAME_DIR_SUFFIX : PAIRING_MODE_SPLIT_DIRS;
    }

    @PostMapping("/detectDirect")
    @RequirePermission("detect:write")
    public Result detectDirect(@RequestParam("file") MultipartFile file,
                               @RequestParam("ptPath") String ptPath,
                               @RequestParam(required = false, defaultValue = "0.25") Double conf,
                               @RequestParam(required = false) Integer userId,
                               @RequestParam(required = false) String userName) {
        log.info("收到直接检测请求: ptPath={}, conf={}, fileName={}", ptPath, conf, file.getOriginalFilename());

        File tempFile = null;
        try {
            String url = getPythonDetectUrl() + "/detect";
            log.info("调用Python服务URL: {}", url);

            tempFile = File.createTempFile("detect_", "_" + file.getOriginalFilename());
            file.transferTo(tempFile);
            log.info("临时文件创建成功: {}, 大小: {} bytes", tempFile.getAbsolutePath(), tempFile.length());

            java.util.HashMap<String, Object> paramMap = new java.util.HashMap<>();
            paramMap.put("file", tempFile);
            paramMap.put("pt_path", ptPath);
            paramMap.put("conf", String.valueOf(conf));
            paramMap.put("save_result", "false");
            paramMap.put("return_image", "true");

            HttpResponse response = HttpRequest.post(url)
                    .form(paramMap)
                    .timeout(120000)
                    .execute();

            log.info("Python服务响应状态: {}", response.getStatus());

            if (response.getStatus() != 200) {
                log.error("Python服务响应异常: {}, 响应体: {}", response.getStatus(), response.body());
                return Result.error("Python服务响应异常: " + response.getStatus());
            }

            String responseBody = response.body();
            cn.hutool.json.JSONObject jsonResponse = JSONUtil.parseObj(responseBody);
            int code = jsonResponse.getInt("code", -1);

            if (code != 0) {
                String message = jsonResponse.getStr("message", "检测失败");
                log.error("Python服务返回错误: {}", message);
                return Result.error(message);
            }

            cn.hutool.json.JSONObject data = jsonResponse.getJSONObject("data");
            String resultImageBase64 = data.getStr("result_image_base64");
            String resultImageUrl = null;

            if (userId != null && resultImageBase64 != null && !resultImageBase64.isEmpty()) {
                String basePath = System.getProperty("user.dir");
                String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
                String resultFileName = "result_" + System.currentTimeMillis() + ".jpg";
                String resultRelativePath = "detect/" + dateDir + "/" + resultFileName;
                String resultFullPath = basePath + "/" + uploadPath + resultRelativePath;

                File resultDir = new File(basePath + "/" + uploadPath + "detect/" + dateDir);
                if (!resultDir.exists()) {
                    resultDir.mkdirs();
                }

                byte[] imageBytes = Base64.getDecoder().decode(resultImageBase64);
                FileUtil.writeBytes(imageBytes, resultFullPath);
                resultImageUrl = fileAccessUrl + resultRelativePath;

                Detect detect = new Detect();
                detect.setUserId(userId);
                detect.setUserName(userName != null ? userName : "匿名用户");
                detect.setOriginalImageName(file.getOriginalFilename());
                detect.setOriginalImageSize(file.getSize());
                detect.setOriginalImageFormat(FileUtil.extName(file.getOriginalFilename()));
                detect.setDetectStatus(2);
                detect.setDetectionData(data.getJSONObject("detection").toString());
                detect.setResultImageUrl(resultRelativePath);
                detect.setAiStatus(0);

                detectService.add(detect);
                data.set("recordId", detect.getId());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("detection", data.getJSONObject("detection"));
            result.put("resultImageBase64", resultImageBase64);
            result.put("resultImageUrl", resultImageUrl);
            result.put("confThreshold", data.get("conf_threshold"));
            result.put("modelPath", data.get("model_path"));
            result.put("recordId", data.get("recordId"));

            return Result.success(result);

        } catch (Exception e) {
            log.error("直接检测失败", e);
            return Result.error("检测失败: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                log.debug("临时文件删除{}: {}", deleted ? "成功" : "失败", tempFile.getAbsolutePath());
            }
        }
    }

    @GetMapping("/listModels")
    @RequirePermission("detect:read")
    public Result listModels() {
        try {
            java.util.List<java.util.Map<String, String>> models =
                    systemConfigService.scanModels("detect", systemConfigService.getModelRootPath("detect"));
            log.info("??? {} ? best.pt ????", models.size());
            return Result.success(models);
        } catch (Exception e) {
            log.error("????????", e);
            return Result.error("????????: " + e.getMessage());
        }
    }

    @PostMapping("/aiAnalysis/{id}")
    @RequirePermission("detect:write")
    public Result aiAnalysis(@PathVariable Integer id,
                             @RequestParam String model) {
        try {
            Detect detect = detectService.selectById(id);
            if (detect == null) {
                return Result.error("检测记录不存在");
            }

            if (detect.getDetectStatus() != 2) {
                return Result.error("请先完成图像检测");
            }

            if (!aiService.isConfigured(model)) {
                return Result.error("AI模型 '" + model + "' 未配置API Key，请联系管理员配置");
            }

            detect.setAiStatus(1);
            detect.setAiModel(model);
            detectService.update(detect);

            log.info("开始AI分析记录: {}, 模型: {}", id, model);

            String prompt = buildAnalysisPrompt(detect);
            String aiResult = cleanAiAnalysisText(aiService.analyze(model, prompt));

            detect.setAiAnalysisResult(aiResult);
            detect.setAiAnalysisTime(LocalDateTime.now());
            detect.setAiStatus(2);
            detectService.update(detect);

            log.info("AI分析完成: detectId={}, model={}", id, model);
            return Result.success(detect);

        } catch (Exception e) {
            log.error("AI分析失败", e);
            Detect detect = new Detect();
            detect.setId(id);
            detect.setAiStatus(3);
            detectService.update(detect);
            return Result.error("AI分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/aiConfigStatus")
    @RequirePermission("detect:read")
    public Result getAiConfigStatus() {
        return Result.success(aiService.getConfigStatus());
    }

    @PostMapping("/testAiConnection")
    @RequirePermission("system:config")
    public Result testAiConnection(@RequestParam String model) {
        try {
            long startTime = System.currentTimeMillis();
            aiService.testConnection(model);
            long responseTime = System.currentTimeMillis() - startTime;

            Map<String, Object> data = new HashMap<>();
            data.put("model", model);
            data.put("responseTime", responseTime);
            return Result.success(data);
        } catch (Exception e) {
            log.error("AI模型连接测试失败: model={}", model, e);
            return Result.error("连接失败: " + e.getMessage());
        }
    }

    private String buildAnalysisPrompt(Detect detect) {
        String detectionData = detect.getDetectionData();
        return String.format(
            "作为一位专业的风力发电机叶片缺陷检测与运维分析专家，请根据以下叶片检测结果进行详细分析：\n\n" +
            "【检测数据】\n%s\n\n" +
            "请提供：\n" +
            "1. 缺陷类型分析 - 损伤(Damage)还是污垢(Dirt)，以及具体特征\n" +
            "2. 位置与范围评估 - 缺陷在叶片上的位置和面积占比\n" +
            "3. 严重程度初步判断 - 根据缺陷大小、位置判断等级\n" +
            "4. 可能原因分析 - 导致该缺陷的可能因素（疲劳/雷击/腐蚀/污染等）\n" +
            "5. 运维处置建议 - 具体的维修或清洁建议\n" +
            "6. 复检与复查建议 - 建议的巡检周期和关注重点\n" +
            "7. 注意事项\n\n" +
            "输出要求：请使用纯文本中文输出，不要使用Markdown格式；不要使用星号*、井号#、反引号`、项目符号等特殊格式符号；标题直接写中文标题加冒号。\n" +
            "最后必须单独输出这一句话：本分析报告仅供参考，仅为辅助检测分析，不替代专业巡检决策",
            detectionData
        );
    }

    private String findDefaultModelPath() {
        return systemConfigService.findDefaultModelPath("detect");
    }

    private String getPythonDetectUrl() {
        return systemConfigService.getPythonDetectUrl(pythonDetectUrl);
    }

    private String cleanAiAnalysisText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replaceAll("[*#`]+", "")
                .replaceAll("(?m)^\\s*[-•]\\s+", "")
                .replaceAll("(?m)^\\s*>\\s*", "")
                .trim();
    }
}
