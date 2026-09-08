package com.example.springb.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.example.springb.annotation.RequirePermission;
import com.example.springb.common.Result;
import com.example.springb.entity.VideoDetect;
import com.example.springb.service.AIService;
import com.example.springb.service.SystemConfigService;
import com.example.springb.service.VideoDetectService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/video")
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    @Resource
    private VideoDetectService videoDetectService;

    @Resource
    private AIService aiService;

    @Resource
    private SystemConfigService systemConfigService;

    @Value("${file.upload.path:uploads/}")
    private String uploadPath;

    @Value("${file.access.url:http://localhost:1234/files/}")
    private String fileAccessUrl;

    @Value("${python.detect.url:http://localhost:6522}")
    private String pythonDetectUrl;

    @Value("${python.video-convert.url:http://localhost:6522}")
    private String pythonVideoConvertUrl;

    private static final List<String> ALLOWED_VIDEO_FORMATS = Arrays.asList("mp4", "avi", "mov", "mkv", "wmv", "flv", "webm");
    private static final List<String> ALLOWED_IMAGE_FORMATS = Arrays.asList("jpg", "jpeg", "png", "bmp", "tiff", "webp");
    private static final List<String> BATCH_OUTPUT_FORMATS = Arrays.asList("mp4", "avi", "webm");
    private static final List<String> BATCH_RESOLUTIONS = Arrays.asList("original", "1920x1080", "1280x720", "640x480");
    private static final long MAX_BATCH_IMAGE_SIZE = 50L * 1024 * 1024;
    private static final long MAX_GENERATED_VIDEO_SIZE = 500L * 1024 * 1024;
    private static final int MAX_BATCH_IMAGE_COUNT = 500;

    // ==================== 基础CRUD接口 ====================

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @RequirePermission("video:read")
    public Result health() {
        return Result.success("Video检测服务运行正常");
    }

    /**
     * 下载检测结果视频（触发浏览器下载对话框）
     */
    @GetMapping("/download/{id}")
    @RequirePermission("video:read")
    public void downloadVideo(@PathVariable Integer id, HttpServletResponse response) {
        try {
            VideoDetect video = videoDetectService.selectById(id);
            if (video == null || video.getResultVideoUrl() == null || video.getResultVideoUrl().isEmpty()) {
                response.setStatus(404);
                response.getWriter().write("视频不存在");
                return;
            }

            String videoUrl = normalizePythonResultVideoUrl(video.getResultVideoUrl());
            String fileName = buildResultVideoFileName(video, id);
            java.io.File videoFile;

            if (videoUrl.startsWith("http")) {
                // Python服务返回的完整URL - 代理下载
                URL remoteUrl = new URL(videoUrl);
                HttpURLConnection conn = (HttpURLConnection) remoteUrl.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("Accept", "video/mp4,video/*,*/*");

                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) {
                    response.setStatus(status);
                    response.getWriter().write("远程视频服务响应异常: " + status);
                    conn.disconnect();
                    return;
                }

                setVideoDownloadHeaders(response, fileName, conn.getContentType(), conn.getContentLengthLong());

                try (java.io.InputStream is = conn.getInputStream();
                     java.io.OutputStream os = response.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    os.flush();
                }
                conn.disconnect();
                return;
            } else {
                // 本地文件路径
                String basePath = System.getProperty("user.dir");
                String fullPath = basePath + "/" + uploadPath + videoUrl;
                videoFile = new java.io.File(fullPath);
            }

            if (!videoFile.exists() || !videoFile.isFile()) {
                response.setStatus(404);
                response.getWriter().write("视频文件不存在: " + videoFile.getAbsolutePath());
                return;
            }

            setVideoDownloadHeaders(response, fileName, "video/mp4", videoFile.length());

            try (java.io.FileInputStream fis = new java.io.FileInputStream(videoFile);
                 java.io.OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }
        } catch (Exception e) {
            log.error("视频下载失败", e);
            try {
                response.setStatus(500);
                response.getWriter().write("下载失败: " + e.getMessage());
            } catch (Exception ignored) {}
        }
    }

    private String buildResultVideoFileName(VideoDetect video, Integer id) {
        String originalName = video.getOriginalVideoName();
        if (originalName == null || originalName.trim().isEmpty()) {
            originalName = "video_" + id + ".mp4";
        }
        int dotIndex = originalName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName;
        String extension = dotIndex > 0 ? originalName.substring(dotIndex + 1) : "mp4";
        if (extension == null || extension.trim().isEmpty()) {
            extension = "mp4";
        }
        return baseName + "_检测结果." + extension;
    }

    private void setVideoDownloadHeaders(HttpServletResponse response, String fileName, String contentType, long contentLength) {
        String safeAsciiName = fileName.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").replaceAll("[^\\x20-\\x7E]", "_");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String responseContentType = (contentType == null || contentType.trim().isEmpty() || "application/octet-stream".equalsIgnoreCase(contentType))
                ? "video/mp4"
                : contentType;

        response.setContentType(responseContentType);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + safeAsciiName + "\"; filename*=UTF-8''" + encodedFileName);
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-cache");
        if (contentLength > 0) {
            response.setHeader("Content-Length", String.valueOf(contentLength));
        }
    }

    /**
     * 分页查询视频检测记录
     */
    @GetMapping("/selectPage")
    @RequirePermission("video:read")
    public Result selectPage(@RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize,
                             VideoDetect videoDetect) {
        return Result.success(videoDetectService.selectPage(pageNum, pageSize, videoDetect));
    }

    /**
     * 根据ID查询
     */
    @GetMapping("/selectById/{id}")
    @RequirePermission("video:read")
    public Result selectById(@PathVariable Integer id) {
        VideoDetect videoDetect = videoDetectService.selectById(id);
        normalizeVideoDetectUrls(videoDetect);
        return Result.success(videoDetect);
    }

    /**
     * 删除视频检测记录
     */
    @DeleteMapping("/delete/{id}")
    @RequirePermission("video:delete")
    public Result delete(@PathVariable Integer id) {
        videoDetectService.deleteById(id);
        return Result.success();
    }

    // ==================== 视频上传与检测接口 ====================

    /**
     * 上传原始视频并创建检测记录
     */
    @PostMapping("/upload")
    @RequirePermission("video:upload")
    public Result uploadVideo(@RequestParam("file") MultipartFile file,
                              @RequestParam("userId") Integer userId,
                              @RequestParam("userName") String userName) {
        log.info("收到视频上传请求: userId={}, userName={}, fileName={}, fileSize={}",
                userId, userName, file.getOriginalFilename(), file.getSize());

        try {
            String originalName = file.getOriginalFilename();
            String ext = FileUtil.extName(originalName);

            // 验证视频格式
            if (!ALLOWED_VIDEO_FORMATS.contains(ext.toLowerCase())) {
                return Result.error("不支持的视频格式: " + ext + "，请上传 mp4/avi/mov/mkv/wmv/flv/webm 格式");
            }

            // 生成唯一文件名
            String newFileName = UUID.randomUUID() + "." + ext;

            // 创建日期目录
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String relativePath = "video/" + dateDir + "/" + newFileName;

            // 构建完整路径
            String basePath = System.getProperty("user.dir");
            String fullDir = basePath + "/" + uploadPath + "video/" + dateDir;
            String fullPath = basePath + "/" + uploadPath + relativePath;

            log.info("视频保存目录: {}", fullDir);
            log.info("视频保存路径: {}", fullPath);

            // 确保目录存在
            File dir = new File(fullDir);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                log.info("创建目录: {}, 结果: {}", fullDir, created);
                if (!created) {
                    return Result.error("创建上传目录失败: " + fullDir);
                }
            }

            // 保存文件
            File destFile = new File(fullPath);
            file.transferTo(destFile);
            log.info("视频保存成功: {}", fullPath);

            // 创建检测记录
            VideoDetect videoDetect = new VideoDetect();
            videoDetect.setUserId(userId);
            videoDetect.setUserName(userName);
            videoDetect.setOriginalVideoName(originalName);
            videoDetect.setOriginalVideoUrl(relativePath);
            videoDetect.setOriginalVideoSize(file.getSize());
            videoDetect.setOriginalVideoFormat(ext);
            videoDetect.setDetectStatus(0); // 待检测
            videoDetect.setAiStatus(0);     // 未分析

            videoDetectService.add(videoDetect);
            log.info("视频检测记录创建成功: id={}", videoDetect.getId());

            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("id", videoDetect.getId());
            result.put("originalVideoUrl", fileAccessUrl + relativePath);
            result.put("originalVideoName", originalName);

            return Result.success(result);

        } catch (IOException e) {
            log.error("视频上传失败", e);
            return Result.error("视频上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("视频上传异常", e);
            return Result.error("视频上传异常: " + e.getMessage());
        }
    }

    /**
     * 批量图片合成为视频并创建待检测记录
     */
    @PostMapping("/imagesToVideo")
    @RequirePermission("video:upload")
    public Result imagesToVideo(@RequestParam("images") MultipartFile[] images,
                                @RequestParam(value = "durationPerImage", defaultValue = "0.3") Double durationPerImage,
                                @RequestParam(value = "outputFormat", defaultValue = "mp4") String outputFormat,
                                @RequestParam(value = "resolution", defaultValue = "original") String resolution,
                                @RequestParam(value = "userId", required = false) Integer userId,
                                @RequestParam(value = "userName", required = false) String userName) {
        File tempDir = null;
        List<File> tempFiles = new ArrayList<>();

        try {
            if (images == null || images.length == 0) {
                return Result.error("未上传图片文件");
            }
            if (images.length > MAX_BATCH_IMAGE_COUNT) {
                return Result.error("图片数量超过限制，最多支持500张");
            }

            durationPerImage = durationPerImage == null ? 0.3 : durationPerImage;
            if (durationPerImage < 0.1 || durationPerImage > 5.0) {
                return Result.error("每张图时长需在0.1到5.0秒之间");
            }

            outputFormat = normalizeLower(outputFormat, "mp4");
            resolution = normalizeLower(resolution, "original");
            if (!BATCH_OUTPUT_FORMATS.contains(outputFormat)) {
                return Result.error("输出格式仅支持 MP4、AVI、WebM");
            }
            if (!BATCH_RESOLUTIONS.contains(resolution)) {
                return Result.error("不支持的分辨率参数: " + resolution);
            }

            tempDir = Files.createTempDirectory("blade_batch_images_").toFile();
            int validIndex = 0;
            for (MultipartFile image : images) {
                if (image == null || image.isEmpty()) {
                    continue;
                }
                if (image.getSize() > MAX_BATCH_IMAGE_SIZE) {
                    log.warn("跳过超大图片: {}, size={}", image.getOriginalFilename(), image.getSize());
                    continue;
                }

                String originalName = image.getOriginalFilename();
                String ext = normalizeLower(FileUtil.extName(originalName), "");
                if (!ALLOWED_IMAGE_FORMATS.contains(ext)) {
                    log.warn("跳过非图片文件: {}", originalName);
                    continue;
                }

                String safeFileName = String.format(Locale.ROOT, "%04d_%s.%s", validIndex++, UUID.randomUUID(), ext);
                File savedFile = new File(tempDir, safeFileName);
                image.transferTo(savedFile);
                tempFiles.add(savedFile);
            }

            if (tempFiles.isEmpty()) {
                return Result.error("未找到有效图片文件，请选择 jpg/png/bmp/tiff/webp 图片");
            }

            cn.hutool.json.JSONObject pythonData = requestImagesToVideo(tempFiles, durationPerImage, outputFormat, resolution);
            String generatedVideoUrl = resolvePythonGeneratedVideoUrl(pythonData);
            if (generatedVideoUrl == null || generatedVideoUrl.isEmpty()) {
                return Result.error("Python服务未返回生成视频地址");
            }

            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String newFileName = UUID.randomUUID() + "." + outputFormat;
            String relativePath = "video/" + dateDir + "/" + newFileName;
            File outputDir = resolveUploadFile("video/" + dateDir);
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                return Result.error("创建视频保存目录失败: " + outputDir.getAbsolutePath());
            }

            File localVideoFile = resolveUploadFile(relativePath);
            downloadFile(generatedVideoUrl, localVideoFile);
            if (!localVideoFile.exists() || localVideoFile.length() == 0) {
                return Result.error("生成视频保存失败");
            }
            if (localVideoFile.length() > MAX_GENERATED_VIDEO_SIZE) {
                if (!localVideoFile.delete()) {
                    log.warn("删除超大生成视频失败: {}", localVideoFile.getAbsolutePath());
                }
                return Result.error("生成视频超过500MB限制");
            }

            VideoDetect videoDetect = new VideoDetect();
            videoDetect.setUserId(userId != null ? userId : 1);
            videoDetect.setUserName((userName != null && !userName.isBlank()) ? userName : "管理员");
            videoDetect.setOriginalVideoName("风机图像智能剪辑_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "." + outputFormat);
            videoDetect.setOriginalVideoUrl(relativePath);
            videoDetect.setOriginalVideoSize(localVideoFile.length());
            videoDetect.setOriginalVideoFormat(outputFormat);
            videoDetect.setDetectStatus(0);
            videoDetect.setAiStatus(0);
            videoDetect.setTotalFrames(pythonData.getInt("total_frames", tempFiles.size()));
            videoDetect.setFps(pythonData.getDouble("fps", 0.0));
            videoDetect.setDuration(pythonData.getDouble("duration", 0.0));

            videoDetectService.add(videoDetect);
            videoDetectService.update(videoDetect);
            log.info("图片转视频记录创建成功: id={}, imageCount={}, path={}", videoDetect.getId(), tempFiles.size(), relativePath);

            Map<String, Object> result = new HashMap<>();
            result.put("id", videoDetect.getId());
            result.put("recordId", videoDetect.getId());
            result.put("videoUrl", fileAccessUrl + relativePath);
            result.put("originalVideoUrl", fileAccessUrl + relativePath);
            result.put("relativePath", relativePath);
            result.put("totalFrames", videoDetect.getTotalFrames());
            result.put("duration", videoDetect.getDuration());
            result.put("fps", videoDetect.getFps());
            result.put("imageCount", pythonData.getInt("image_count", tempFiles.size()));

            return Result.success(result);

        } catch (Exception e) {
            log.error("批量图片转视频失败", e);
            return Result.error("转换失败: " + e.getMessage());
        } finally {
            for (File file : tempFiles) {
                if (file != null && file.exists() && !file.delete()) {
                    log.debug("清理临时图片失败: {}", file.getAbsolutePath());
                }
            }
            if (tempDir != null && tempDir.exists() && !tempDir.delete()) {
                log.debug("清理临时目录失败: {}", tempDir.getAbsolutePath());
            }
        }
    }

    /**
     * 调用Python检测服务进行视频检测
     *
     * 参数:
     * - id: 检测记录ID
     * - ptPath: 模型文件路径 (可选)
     * - conf: 置信度阈值 (可选，默认0.25)
     * - skipFrames: 跳帧处理 (可选，默认0=每帧处理)
     */
    @PostMapping("/startDetect/{id}")
    @RequirePermission("video:upload")
    public Result startDetect(@PathVariable Integer id,
                              @RequestParam(required = false) String ptPath,
                              @RequestParam(required = false) Double conf,
                              @RequestParam(required = false, defaultValue = "0") Integer skipFrames) {
        if (ptPath == null || ptPath.isEmpty()) {
            ptPath = findDefaultModelPath();
        }
        if (conf == null) {
            conf = systemConfigService.getDoubleValue("default_threshold", "video", SystemConfigService.DEFAULT_VIDEO_THRESHOLD);
        }

        try {
            VideoDetect videoDetect = videoDetectService.selectById(id);
            if (videoDetect == null) {
                return Result.error("检测记录不存在");
            }

            // 更新为检测中状态
            videoDetect.setDetectStatus(1);
            videoDetectService.update(videoDetect);

            log.info("开始视频检测记录: {}, ptPath={}, conf={}, skipFrames={}", id, ptPath, conf, skipFrames);

            // 构建视频完整路径
            String basePath = System.getProperty("user.dir");
            String videoPath = basePath + "/" + uploadPath + videoDetect.getOriginalVideoUrl();

            File videoFile = new File(videoPath);
            if (!videoFile.exists()) {
                throw new RuntimeException("视频文件不存在: " + videoPath);
            }

            // 调用Python检测服务（使用更长的超时时间，视频处理较慢）
            String url = getPythonDetectUrl() + "/detect_video";
            log.info("调用Python视频检测服务: {}", url);

            HttpResponse response = HttpRequest.post(url)
                    .form("file", videoFile)
                    .form("pt_path", ptPath)
                    .form("conf", String.valueOf(conf))
                    .form("skip_frames", String.valueOf(skipFrames))
                    .timeout(600000) // 10分钟超时
                    .execute();

            if (response.getStatus() != 200) {
                throw new RuntimeException("Python服务响应异常: " + response.getStatus());
            }

            // 解析响应
            String responseBody = response.body();
            log.debug("Python视频检测服务响应: {}", responseBody);

            cn.hutool.json.JSONObject jsonResponse = JSONUtil.parseObj(responseBody);
            int code = jsonResponse.getInt("code", -1);

            if (code != 0) {
                String message = jsonResponse.getStr("message", "视频检测失败");
                throw new RuntimeException(message);
            }

            // 解析检测结果
            cn.hutool.json.JSONObject data = jsonResponse.getJSONObject("data");

            // 获取结果视频URL（优先使用Python直连URL，避免文件拷贝和编码兼容问题）
            String resultVideoUrl = normalizePythonResultVideoUrl(data.getStr("result_video_url"));
            log.info("Python结果视频URL: {}", resultVideoUrl);

            // 构建detection_data（包含检测汇总和帧数据）
            cn.hutool.json.JSONObject detectionSummary = data.getJSONObject("detection_summary");
            cn.hutool.json.JSONObject videoProperties = data.getJSONObject("video_properties");
            cn.hutool.json.JSONArray frameResults = data.getJSONArray("frame_results");

            // 将完整数据合并为一个JSON对象存储
            cn.hutool.json.JSONObject detectionData = new cn.hutool.json.JSONObject();
            if (detectionSummary != null) {
                detectionData.putAll(detectionSummary);
            }
            if (videoProperties != null) {
                detectionData.putAll(videoProperties);
            }
            if (frameResults != null) {
                detectionData.set("frame_results", frameResults);
            }

            // 更新检测记录
            videoDetect.setDetectStatus(2); // 检测完成
            videoDetect.setDetectionData(detectionData.toString());
            if (resultVideoUrl != null && !resultVideoUrl.isEmpty()) {
                videoDetect.setResultVideoUrl(resultVideoUrl);
            }
            if (videoProperties != null) {
                videoDetect.setTotalFrames(videoProperties.getInt("total_frames"));
                videoDetect.setFps(videoProperties.getDouble("fps"));
                videoDetect.setDuration(videoProperties.getDouble("duration"));
            }
            videoDetectService.update(videoDetect);

            return Result.success(videoDetect);

        } catch (Exception e) {
            log.error("视频检测失败", e);
            VideoDetect update = new VideoDetect();
            update.setId(id);
            update.setDetectStatus(3); // 检测失败
            videoDetectService.update(update);
            return Result.error("视频检测失败: " + e.getMessage());
        }
    }

    // ==================== AI分析接口 ====================

    /**
     * 调用AI大模型进行视频检测结果辅助分析
     */
    @PostMapping("/aiAnalysis/{id}")
    @RequirePermission("video:upload")
    public Result aiAnalysis(@PathVariable Integer id,
                             @RequestParam String model) {
        try {
            VideoDetect videoDetect = videoDetectService.selectById(id);
            if (videoDetect == null) {
                return Result.error("检测记录不存在");
            }

            if (videoDetect.getDetectStatus() != 2) {
                return Result.error("请先完成视频检测");
            }

            if (!aiService.isConfigured(model)) {
                return Result.error("AI模型 '" + model + "' 未配置API Key，请联系管理员配置");
            }

            videoDetect.setAiStatus(1);
            videoDetect.setAiModel(model);
            videoDetectService.update(videoDetect);

            log.info("开始AI分析视频记录: {}, 模型: {}", id, model);

            String prompt = buildAnalysisPrompt(videoDetect);
            String aiResult = cleanAiAnalysisText(aiService.analyze(model, prompt));

            videoDetect.setAiAnalysisResult(aiResult);
            videoDetect.setAiAnalysisTime(LocalDateTime.now());
            videoDetect.setAiStatus(2);
            videoDetectService.update(videoDetect);

            log.info("AI分析完成: videoDetectId={}, model={}", id, model);
            return Result.success(videoDetect);

        } catch (Exception e) {
            log.error("AI分析失败", e);
            VideoDetect update = new VideoDetect();
            update.setId(id);
            update.setAiStatus(3);
            videoDetectService.update(update);
            return Result.error("AI分析失败: " + e.getMessage());
        }
    }

    /**
     * 获取AI模型配置状态
     */
    @GetMapping("/aiConfigStatus")
    @RequirePermission("video:read")
    public Result getAiConfigStatus() {
        return Result.success(aiService.getConfigStatus());
    }

    // ==================== 模型列表接口 ====================

    /**
     * 列出可用的检测模型（复用与detect相同的模型目录）
     */
    @GetMapping("/listModels")
    @RequirePermission("video:read")
    public Result listModels() {
        try {
            java.util.List<java.util.Map<String, String>> models =
                    systemConfigService.scanModels("video", systemConfigService.getModelRootPath("video"));
            log.info("??? {} ? best.pt ????", models.size());
            return Result.success(models);
        } catch (Exception e) {
            log.error("????????", e);
            return Result.error("????????: " + e.getMessage());
        }
    }

    // ==================== ???? ====================

    private String findDefaultModelPath() {
        return systemConfigService.findDefaultModelPath("video");
    }

    private String normalizeLower(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void normalizeVideoDetectUrls(VideoDetect videoDetect) {
        if (videoDetect == null) {
            return;
        }
        if (videoDetect.getResultVideoUrl() != null && !videoDetect.getResultVideoUrl().isBlank()) {
            videoDetect.setResultVideoUrl(normalizePythonResultVideoUrl(videoDetect.getResultVideoUrl()));
        }
        if (videoDetect.getOriginalVideoUrl() != null && !videoDetect.getOriginalVideoUrl().isBlank()) {
            videoDetect.setOriginalVideoUrl(videoDetect.getOriginalVideoUrl().trim());
        }
    }

    private File resolveUploadFile(String relativePath) {
        File uploadRoot = new File(uploadPath);
        if (!uploadRoot.isAbsolute()) {
            uploadRoot = new File(System.getProperty("user.dir"), uploadPath);
        }
        return new File(uploadRoot, relativePath);
    }

    private cn.hutool.json.JSONObject requestImagesToVideo(List<File> images,
                                                           Double durationPerImage,
                                                           String outputFormat,
                                                           String resolution) {
        String url = pythonVideoConvertUrl + "/images_to_video";
        log.info("调用Python图片转视频服务: {}, imageCount={}", url, images.size());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (File image : images) {
            body.add("images", new FileSystemResource(image));
        }
        body.add("duration_per_image", String.valueOf(durationPerImage));
        body.add("output_format", outputFormat);
        body.add("resolution", resolution);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(30000);
        requestFactory.setReadTimeout(300000);

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Python服务响应异常: " + response.getStatusCode().value());
        }

        cn.hutool.json.JSONObject jsonResponse = JSONUtil.parseObj(response.getBody());
        int code = jsonResponse.getInt("code", -1);
        if (code != 200 && code != 0) {
            throw new RuntimeException(jsonResponse.getStr("msg", "视频生成失败"));
        }

        cn.hutool.json.JSONObject data = jsonResponse.getJSONObject("data");
        if (data == null) {
            throw new RuntimeException("Python服务未返回视频数据");
        }
        return data;
    }

    private String resolvePythonGeneratedVideoUrl(cn.hutool.json.JSONObject pythonData) {
        String resolved = resolvePythonGeneratedVideoUrlCompat(pythonData);
        if (resolved != null && !resolved.isEmpty()) {
            return resolved;
        }

        String videoUrlFull = pythonData.getStr("video_url_full");
        if (videoUrlFull != null && !videoUrlFull.isEmpty()) {
            return videoUrlFull;
        }

        String relativePythonPath = pythonData.getStr("video_url");
        if (relativePythonPath == null || relativePythonPath.isEmpty()) {
            return "";
        }
        return pythonVideoConvertUrl.replaceAll("/+$", "") + "/" + relativePythonPath.replace("\\", "/").replaceAll("^/+", "");
    }

    private String resolvePythonGeneratedVideoUrlCompat(cn.hutool.json.JSONObject pythonData) {
        List<String> candidates = Arrays.asList(
                pythonData.getStr("video_url_full"),
                pythonData.getStr("video_url"),
                pythonData.getStr("result_video_url")
        );
        for (String candidate : candidates) {
            String resolved = resolvePythonGeneratedVideoLocation(candidate);
            if (resolved != null && !resolved.isEmpty()) {
                return resolved;
            }
        }
        return "";
    }

    private String resolvePythonGeneratedVideoLocation(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }

        File localFile = resolvePythonGeneratedVideoFile(rawValue);
        if (localFile != null && localFile.exists()) {
            return localFile.getAbsolutePath();
        }

        String generatedVideoRelativePath = extractGeneratedVideoRelativePath(rawValue);
        if (!generatedVideoRelativePath.isEmpty()) {
            String rewrittenUrl = pythonVideoConvertUrl.replaceAll("/+$", "") + "/static/" + generatedVideoRelativePath;
            log.warn("Python图片转视频返回了本地路径风格地址，已改写为结果访问地址: {} -> {}", rawValue, rewrittenUrl);
            return rewrittenUrl;
        }

        String normalizedValue = rawValue.trim().replace("\\", "/");
        if (normalizedValue.startsWith("/results/") || normalizedValue.startsWith("results/")) {
            return pythonVideoConvertUrl.replaceAll("/+$", "") + "/" + normalizedValue.replaceAll("^/+", "");
        }
        if (normalizedValue.startsWith("http://") || normalizedValue.startsWith("https://")) {
            return normalizedValue;
        }
        return pythonVideoConvertUrl.replaceAll("/+$", "") + "/" + normalizedValue.replaceAll("^/+", "");
    }

    private File resolvePythonGeneratedVideoFile(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String normalizedValue = rawValue.trim().replace("\\", "/");
        if (normalizedValue.startsWith("http://") || normalizedValue.startsWith("https://")) {
            try {
                normalizedValue = new URL(normalizedValue).getPath();
            } catch (Exception e) {
                log.debug("解析Python返回的视频地址失败: {}", rawValue, e);
            }
        }

        int staticIndex = normalizedValue.indexOf("static/generated_videos/");
        if (staticIndex >= 0) {
            String relativeStaticPath = normalizedValue.substring(staticIndex).replace("/", File.separator);
            File staticFile = new File(System.getProperty("user.dir"), relativeStaticPath);
            if (staticFile.exists()) {
                log.info("检测到Python生成视频位于本地静态目录，直接读取文件: {}", staticFile.getAbsolutePath());
                return staticFile;
            }
        }

        File directFile = new File(rawValue);
        if (directFile.exists()) {
            return directFile;
        }
        return null;
    }

    private String extractGeneratedVideoRelativePath(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }

        String normalizedValue = rawValue.trim().replace("\\", "/");
        if (normalizedValue.startsWith("http://") || normalizedValue.startsWith("https://")) {
            try {
                normalizedValue = new URL(normalizedValue).getPath();
            } catch (Exception e) {
                log.debug("解析Python返回的视频地址失败: {}", rawValue, e);
            }
        }

        int staticIndex = normalizedValue.indexOf("static/generated_videos/");
        if (staticIndex < 0) {
            return "";
        }
        return normalizedValue.substring(staticIndex + "static/".length()).replaceAll("^/+", "");
    }

    private String normalizePythonResultVideoUrl(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }

        String normalizedValue = rawValue.trim().replace("\\", "/");
        String resultRelativePath = extractResultVideoRelativePath(normalizedValue);
        if (!resultRelativePath.isEmpty()) {
            String normalizedUrl = getPythonDetectUrl().replaceAll("/+$", "") + "/results/" + resultRelativePath;
            if (!normalizedUrl.equals(normalizedValue)) {
                log.warn("Python瑙嗛妫€娴嬬粨鏋滃湴鍧€宸茶瑙勮寖鍖? {} -> {}", rawValue, normalizedUrl);
            }
            return normalizedUrl;
        }

        File localFile = resolvePythonResultVideoFile(rawValue);
        if (localFile != null && localFile.exists()) {
            return localFile.getAbsolutePath();
        }

        if (normalizedValue.startsWith("/results/") || normalizedValue.startsWith("results/")) {
            return getPythonDetectUrl().replaceAll("/+$", "") + "/" + normalizedValue.replaceAll("^/+", "");
        }
        if (normalizedValue.startsWith("http://") || normalizedValue.startsWith("https://")) {
            return normalizedValue;
        }
        return normalizedValue;
    }

    private File resolvePythonResultVideoFile(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String normalizedValue = rawValue.trim().replace("\\", "/");
        if (normalizedValue.startsWith("http://") || normalizedValue.startsWith("https://")) {
            try {
                normalizedValue = new URL(normalizedValue).getPath();
            } catch (Exception e) {
                log.debug("瑙ｆ瀽Python瑙嗛妫€娴嬬粨鏋滃湴鍧€澶辫触: {}", rawValue, e);
            }
        }

        int resultIndex = normalizedValue.indexOf("results/");
        if (resultIndex >= 0) {
            String relativeResultPath = normalizedValue.substring(resultIndex).replace("/", File.separator);
            File resultFile = new File(System.getProperty("user.dir"), relativeResultPath);
            if (resultFile.exists()) {
                return resultFile;
            }
        }

        File directFile = new File(rawValue);
        if (directFile.exists()) {
            return directFile;
        }
        return null;
    }

    private String extractResultVideoRelativePath(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }

        String normalizedValue = rawValue.trim().replace("\\", "/");
        if (normalizedValue.startsWith("http://") || normalizedValue.startsWith("https://")) {
            try {
                normalizedValue = new URL(normalizedValue).getPath();
            } catch (Exception e) {
                log.debug("瑙ｆ瀽Python瑙嗛妫€娴嬬粨鏋滃湴鍧€澶辫触: {}", rawValue, e);
            }
        }

        int resultIndex = normalizedValue.indexOf("results/");
        if (resultIndex < 0) {
            return "";
        }
        return normalizedValue.substring(resultIndex + "results/".length()).replaceAll("^/+", "");
    }

    private List<String> buildVideoDownloadCandidates(String fileUrl) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (fileUrl == null || fileUrl.isBlank()) {
            return new ArrayList<>();
        }

        String normalizedValue = fileUrl.trim().replace("\\", "/");
        candidates.add(normalizedValue);

        String generatedVideoRelativePath = extractGeneratedVideoRelativePath(normalizedValue);
        if (!generatedVideoRelativePath.isEmpty()) {
            String baseUrl = pythonVideoConvertUrl.replaceAll("/+$", "");
            candidates.add(baseUrl + "/static/" + generatedVideoRelativePath);
            candidates.add(baseUrl + "/results/" + generatedVideoRelativePath);
            candidates.add(baseUrl + "/" + generatedVideoRelativePath);
        }

        return new ArrayList<>(candidates);
    }

    private void downloadFile(String fileUrl, File targetFile) throws IOException {
        IOException lastException = null;
        for (String candidate : buildVideoDownloadCandidates(fileUrl)) {
            File localCandidate = resolvePythonGeneratedVideoFile(candidate);
            if (localCandidate != null && localCandidate.exists()) {
                log.info("妫€娴嬪埌Python鐢熸垚瑙嗛浣嶄簬鏈湴闈欐€佺洰褰曪紝鐩存帴澶嶅埗鏂囦欢: {}", localCandidate.getAbsolutePath());
                Files.copy(localCandidate.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            try {
                log.info("涓嬭浇鐢熸垚瑙嗛: {} -> {}", candidate, targetFile.getAbsolutePath());
                try (InputStream inputStream = new URL(candidate).openStream();
                     FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                return;
            } catch (IOException e) {
                lastException = e;
                log.warn("涓嬭浇鐢熸垚瑙嗛澶辫触锛屽皾璇曚笅涓€涓湴鍧€: {}", candidate);
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        File localSource = resolvePythonGeneratedVideoFile(fileUrl);
        if (localSource != null && localSource.exists()) {
            log.info("检测到Python生成视频位于本地静态目录，直接复制文件: {}", localSource.getAbsolutePath());
            Files.copy(localSource.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        log.info("下载生成视频: {} -> {}", fileUrl, targetFile.getAbsolutePath());
        try (InputStream inputStream = new URL(fileUrl).openStream();
             FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * 构建AI分析Prompt（视频版）
     */
    private String buildAnalysisPrompt(VideoDetect videoDetect) {
        String detectionData = videoDetect.getDetectionData();
        return String.format(
            "作为一位专业的风力发电机叶片缺陷检测与运维分析专家，请根据以下视频检测结果进行详细分析：\n\n" +
            "【视频检测数据】\n%s\n\n" +
            "【分析要求】\n" +
            "1. 缺陷类别分析 - 视频中检测到的主要缺陷类型及其分布\n" +
            "2. 严重程度评估 - 基于缺陷出现频率和置信度评估叶片的总体状况\n" +
            "3. 时序变化分析 - 分析缺陷在视频不同帧中的变化情况\n" +
            "4. 可能原因分析 - 结合缺陷类型分析可能的原因\n" +
            "5. 运维处置建议 - 给出具体的维修或清洁建议\n" +
            "6. 复检与复查建议 - 建议的巡检周期和关注重点\n" +
            "7. 注意事项\n\n" +
            "输出要求：请使用纯文本中文输出，不要使用Markdown格式；不要使用星号*、井号#、反引号`、项目符号等特殊格式符号；标题直接写中文标题加冒号。\n" +
            "最后必须单独输出这一句话：本分析报告仅供参考，仅为辅助检测分析，不替代专业巡检决策",
            detectionData
        );
    }

    /**
     * 清理AI返回的Markdown格式符号
     */
    private String cleanAiAnalysisText(String text) {
        if (text == null) return "";
        return text
                .replace("\r\n", "\n")
                .replaceAll("[*#`]+", "")
                .replaceAll("(?m)^\\s*[-•]\\s+", "")
                .replaceAll("(?m)^\\s*>\\s*", "")
                .trim();
    }

    private String getPythonDetectUrl() {
        return systemConfigService.getPythonDetectUrl(pythonDetectUrl);
    }
}
