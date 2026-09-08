package com.example.springb.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.springb.entity.SystemConfig;
import com.example.springb.entity.UiBrandingConfig;
import com.example.springb.entity.UiMenuGroupConfig;
import com.example.springb.entity.UiRouteConfig;
import com.example.springb.exception.CustomerException;
import com.example.springb.mapper.SystemConfigMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SystemConfigService {

    private static final Logger log = LoggerFactory.getLogger(SystemConfigService.class);

    public static final String DEFAULT_MODEL_ROOT = "D:/algorithms/V11-dmt/runs/race-blade";
    public static final double DEFAULT_DETECT_THRESHOLD = 0.55;
    public static final double DEFAULT_VIDEO_THRESHOLD = 0.6;
    private static final String LEGACY_PYTHON_DETECT_URL = "http://localhost:5001";
    private static final String DEFAULT_PYTHON_DETECT_URL = "http://localhost:6522";
    private static final String MODEL_ALIASES_KEY = "model_aliases";
    private static final String HIDDEN_MODELS_KEY = "hidden_models";
    private static final String UI_CONFIG_GROUP = "ui";
    private static final String UI_BRANDING_KEY = "branding";
    private static final String UI_APP_TITLE_KEY = "app_title";
    private static final String UI_LOGIN_TITLE_KEY = "login_title";
    private static final String UI_LOGO_URL_KEY = "logo_url";
    private static final String UI_SHOW_LOGO_KEY = "show_logo";
    private static final String UI_FAVICON_URL_KEY = "favicon_url";
    private static final String UI_SHOW_FAVICON_KEY = "show_favicon";
    private static final String GLOBAL_LOGIN_DEFAULT_ROUTE_KEY = "login_default_route";
    private static final String UI_MENU_GROUP_PREFIX = "menu_group_";
    private static final String UI_ROUTE_PREFIX = "route_";
    private static final List<String> SUPPORTED_MODEL_EXTENSIONS = List.of("pt", "engine", "trt");
    private static final String DEFAULT_APP_TITLE = "基于大模型和多模态数据融合的风机缺陷智能检测系统";
    private static final String DEFAULT_LOGO_URL = "/lyw.png";

    @Resource
    private SystemConfigMapper systemConfigMapper;

    public List<SystemConfig> getByGroup(String configGroup) {
        return systemConfigMapper.selectByGroup(configGroup);
    }

    public List<SystemConfig> getAll() {
        return systemConfigMapper.selectAll();
    }

    public SystemConfig getByKeyAndGroup(String configKey, String configGroup) {
        return systemConfigMapper.selectByKeyAndGroup(configKey, configGroup);
    }

    public String getValue(String configKey, String configGroup) {
        SystemConfig config = getByKeyAndGroup(configKey, configGroup);
        return config == null ? null : config.getConfigValue();
    }

    public String getValue(String configKey, String configGroup, String defaultValue) {
        String value = getValue(configKey, configGroup);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    public double getDoubleValue(String configKey, String configGroup, double defaultValue) {
        String value = getValue(configKey, configGroup);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("Invalid numeric config: {}.{}={}", configGroup, configKey, value);
            return defaultValue;
        }
    }

    public void updateValue(String configKey, String configGroup, String configValue) {
        int updated = systemConfigMapper.updateValueByKeyAndGroup(configKey, configGroup, configValue);
        if (updated <= 0) {
            throw new CustomerException("400", "配置项不存在: " + configGroup + "." + configKey);
        }
    }

    public void saveConfig(SystemConfig config) {
        if (config == null || config.getConfigKey() == null || config.getConfigGroup() == null) {
            throw new CustomerException("400", "配置项不能为空");
        }
        SystemConfig existing = getByKeyAndGroup(config.getConfigKey(), config.getConfigGroup());
        if (existing == null) {
            if (config.getConfigLabel() == null || config.getConfigLabel().trim().isEmpty()) {
                config.setConfigLabel(config.getConfigKey());
            }
            if (config.getDefaultValue() == null) {
                config.setDefaultValue("");
            }
            if (config.getSortOrder() == null) {
                config.setSortOrder(999);
            }
            systemConfigMapper.insert(config);
            return;
        }
        updateValue(config.getConfigKey(), config.getConfigGroup(), config.getConfigValue());
    }

    public void batchUpdate(List<SystemConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (SystemConfig config : configs) {
            saveConfig(config);
        }
    }

    public void resetToDefault(String configKey, String configGroup) {
        SystemConfig config = getByKeyAndGroup(configKey, configGroup);
        if (config == null) {
            throw new CustomerException("400", "配置项不存在: " + configGroup + "." + configKey);
        }
        updateValue(configKey, configGroup, config.getDefaultValue());
    }

    public List<Map<String, String>> scanModels(String rootPath) {
        String scanRoot = rootPath == null || rootPath.trim().isEmpty() ? DEFAULT_MODEL_ROOT : rootPath.trim();
        File modelFolder = new File(scanRoot);
        if (!modelFolder.exists() || !modelFolder.isDirectory()) {
            throw new CustomerException("400", "模型目录不存在: " + scanRoot);
        }

        List<File> modelFiles = new ArrayList<>();
        collectModelFiles(modelFolder, modelFiles);

        List<Map<String, String>> models = new ArrayList<>();
        for (File modelFile : modelFiles) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("label", extractModelLabel(modelFile));
            item.put("value", modelFile.getAbsolutePath());
            models.add(item);
        }
        models.sort(Comparator.comparing(item -> item.get("label"), String.CASE_INSENSITIVE_ORDER));
        return models;
    }

    public List<Map<String, String>> scanModels(String group, String rootPath) {
        return scanModels(group, rootPath, false);
    }

    public List<Map<String, String>> scanModels(String group, String rootPath, boolean includeHidden) {
        List<Map<String, String>> scanned = scanModels(rootPath);
        Map<String, String> aliases = getModelAliases(group);
        Set<String> hiddenModels = getHiddenModels(group);
        List<Map<String, String>> models = new ArrayList<>();
        for (Map<String, String> item : scanned) {
            String modelPath = item.get("value");
            boolean hidden = hiddenModels.contains(modelPath);
            if (hidden && !includeHidden) {
                continue;
            }
            String originalLabel = item.get("label");
            String customLabel = aliases.getOrDefault(modelPath, "").trim();

            Map<String, String> enriched = new LinkedHashMap<>();
            enriched.put("label", customLabel.isEmpty() ? originalLabel : customLabel);
            enriched.put("value", modelPath);
            enriched.put("originalLabel", originalLabel);
            enriched.put("customLabel", customLabel);
            enriched.put("hidden", String.valueOf(hidden));
            models.add(enriched);
        }
        models.sort(Comparator.comparing(item -> item.get("label"), String.CASE_INSENSITIVE_ORDER));
        return models;
    }

    public String getModelRootPath(String group) {
        return getValue("model_root_path", group, DEFAULT_MODEL_ROOT);
    }

    public String getPythonDetectUrl(String fallbackUrl) {
        SystemConfig config = getByKeyAndGroup("python_detect_url", "global");
        if (config == null || config.getConfigValue() == null || config.getConfigValue().trim().isEmpty()) {
            return fallbackUrl == null || fallbackUrl.trim().isEmpty() ? DEFAULT_PYTHON_DETECT_URL : fallbackUrl;
        }
        String value = config.getConfigValue();
        if (LEGACY_PYTHON_DETECT_URL.equals(value.trim())) {
            try {
                config.setConfigValue(DEFAULT_PYTHON_DETECT_URL);
                if (LEGACY_PYTHON_DETECT_URL.equals(config.getDefaultValue())) {
                    config.setDefaultValue(DEFAULT_PYTHON_DETECT_URL);
                }
                systemConfigMapper.updateById(config);
            } catch (Exception ex) {
                log.warn("Failed to migrate python_detect_url to {}", DEFAULT_PYTHON_DETECT_URL, ex);
            }
            return DEFAULT_PYTHON_DETECT_URL;
        }
        return value.trim();
    }

    public String findDefaultModelPath(String group) {
        String rootPath = getModelRootPath(group);
        List<Map<String, String>> models = scanModels(group, rootPath);
        if (models.isEmpty()) {
            log.warn("No supported model file found under {}", rootPath);
            return rootPath;
        }
        return models.get(0).get("value");
    }

    public UiBrandingConfig getUiBrandingConfig() {
        UiBrandingConfig config = readLegacyUiBrandingConfig();
        if (config == null) {
            config = defaultUiBrandingConfig();
        } else {
            config = normalizeUiBrandingConfig(config);
        }

        config.setAppTitle(getValue(UI_APP_TITLE_KEY, UI_CONFIG_GROUP, config.getAppTitle()));
        config.setLoginTitle(getValue(UI_LOGIN_TITLE_KEY, UI_CONFIG_GROUP, config.getLoginTitle()));
        config.setLogoUrl(getValue(UI_LOGO_URL_KEY, UI_CONFIG_GROUP, config.getLogoUrl()));
        config.setShowLogo(getBooleanValue(UI_SHOW_LOGO_KEY, UI_CONFIG_GROUP, Boolean.TRUE.equals(config.getShowLogo())));
        config.setFaviconUrl(getValue(UI_FAVICON_URL_KEY, UI_CONFIG_GROUP, config.getFaviconUrl()));
        config.setShowFavicon(getBooleanValue(UI_SHOW_FAVICON_KEY, UI_CONFIG_GROUP, Boolean.TRUE.equals(config.getShowFavicon())));
        config.setLoginDefaultRoute(getValue(GLOBAL_LOGIN_DEFAULT_ROUTE_KEY, "global", config.getLoginDefaultRoute()));

        List<UiMenuGroupConfig> groups = new ArrayList<>();
        for (UiMenuGroupConfig item : config.getMenuGroups()) {
            UiMenuGroupConfig group = new UiMenuGroupConfig();
            group.setKey(item.getKey());
            group.setTitle(getValue(menuGroupTitleKey(item.getKey()), UI_CONFIG_GROUP, item.getTitle()));
            groups.add(group);
        }
        config.setMenuGroups(groups);

        List<UiRouteConfig> routes = new ArrayList<>();
        for (UiRouteConfig item : config.getRoutes()) {
            UiRouteConfig route = new UiRouteConfig();
            route.setPath(item.getPath());
            route.setTitle(getValue(routeTitleKey(item.getPath()), UI_CONFIG_GROUP, item.getTitle()));
            boolean defaultVisible = item.getVisible() == null || item.getVisible();
            route.setVisible("/manager/config".equals(item.getPath())
                    ? Boolean.TRUE
                    : getBooleanValue(routeVisibleKey(item.getPath()), UI_CONFIG_GROUP, defaultVisible));
            routes.add(route);
        }
        config.setRoutes(routes);

        return normalizeUiBrandingConfig(config);
    }

    public void saveUiBrandingConfig(UiBrandingConfig config) {
        UiBrandingConfig normalized = normalizeUiBrandingConfig(config);
        UiBrandingConfig defaults = defaultUiBrandingConfig();

        saveUiConfigValue(UI_APP_TITLE_KEY, "界面系统标题", normalized.getAppTitle(), defaults.getAppTitle(), 1);
        saveUiConfigValue(UI_LOGIN_TITLE_KEY, "登录页标题", normalized.getLoginTitle(), defaults.getLoginTitle(), 2);
        saveUiConfigValue(UI_LOGO_URL_KEY, "Logo路径", normalized.getLogoUrl(), defaults.getLogoUrl(), 3);
        saveUiConfigValue(UI_SHOW_LOGO_KEY, "是否显示Logo", String.valueOf(Boolean.TRUE.equals(normalized.getShowLogo())), "true", 4);
        saveUiConfigValue(UI_FAVICON_URL_KEY, "标签页Logo路径", normalized.getFaviconUrl(), defaults.getFaviconUrl(), 5);
        saveUiConfigValue(UI_SHOW_FAVICON_KEY, "是否显示标签页Logo", String.valueOf(Boolean.TRUE.equals(normalized.getShowFavicon())), "true", 6);

        int sortOrder = 10;
        for (UiMenuGroupConfig group : normalized.getMenuGroups()) {
            String defaultTitle = defaults.getMenuGroups().stream()
                    .filter(item -> item.getKey().equals(group.getKey()))
                    .map(UiMenuGroupConfig::getTitle)
                    .findFirst()
                    .orElse(group.getTitle());
            saveUiConfigValue(menuGroupTitleKey(group.getKey()), "菜单分组标题-" + group.getKey(), group.getTitle(), defaultTitle, sortOrder++);
        }

        sortOrder = 20;
        for (UiRouteConfig route : normalized.getRoutes()) {
            UiRouteConfig defaultRoute = defaults.getRoutes().stream()
                    .filter(item -> item.getPath().equals(route.getPath()))
                    .findFirst()
                    .orElse(route);
            saveUiConfigValue(routeTitleKey(route.getPath()), "路由标题-" + route.getPath(), route.getTitle(), defaultRoute.getTitle(), sortOrder++);
            saveUiConfigValue(
                    routeVisibleKey(route.getPath()),
                    "路由显示-" + route.getPath(),
                    String.valueOf(Boolean.TRUE.equals(route.getVisible())),
                    String.valueOf(defaultRoute.getVisible() == null || defaultRoute.getVisible()),
                    sortOrder++
            );
        }
    }

    public Map<String, String> getModelAliases(String group) {
        ensureModelAliasesConfig(group);
        String rawValue = getValue(MODEL_ALIASES_KEY, group, "{}");
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            JSONObject jsonObject = JSONUtil.parseObj(rawValue);
            Map<String, String> aliases = new LinkedHashMap<>();
            for (String key : jsonObject.keySet()) {
                aliases.put(key, jsonObject.getStr(key, ""));
            }
            return aliases;
        } catch (Exception ex) {
            log.warn("Invalid model aliases config for group {}: {}", group, rawValue, ex);
            return new LinkedHashMap<>();
        }
    }

    public Set<String> getHiddenModels(String group) {
        ensureHiddenModelsConfig(group);
        String rawValue = getValue(HIDDEN_MODELS_KEY, group, "[]");
        Set<String> hiddenModels = new HashSet<>();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return hiddenModels;
        }
        try {
            for (Object item : JSONUtil.parseArray(rawValue)) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    hiddenModels.add(String.valueOf(item).trim());
                }
            }
        } catch (Exception ex) {
            log.warn("Invalid hidden models config for group {}: {}", group, rawValue, ex);
        }
        return hiddenModels;
    }

    public void saveModelAliases(String group, Map<String, String> aliases) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (aliases != null) {
            aliases.forEach((path, alias) -> {
                if (path == null || path.trim().isEmpty()) {
                    return;
                }
                normalized.put(path.trim(), alias == null ? "" : alias.trim());
            });
        }

        SystemConfig config = new SystemConfig();
        config.setConfigKey(MODEL_ALIASES_KEY);
        config.setConfigGroup(group);
        config.setConfigLabel("模型自定义名称");
        config.setConfigValue(JSONUtil.toJsonStr(normalized));
        config.setDefaultValue("{}");
        config.setSortOrder(20);
        saveConfig(config);
    }

    public void saveHiddenModels(String group, List<String> hiddenModels) {
        List<String> normalized = new ArrayList<>();
        if (hiddenModels != null) {
            for (String item : hiddenModels) {
                if (item != null && !item.trim().isEmpty() && !normalized.contains(item.trim())) {
                    normalized.add(item.trim());
                }
            }
        }

        SystemConfig config = new SystemConfig();
        config.setConfigKey(HIDDEN_MODELS_KEY);
        config.setConfigGroup(group);
        config.setConfigLabel("前端隐藏模型");
        config.setConfigValue(JSONUtil.toJsonStr(normalized));
        config.setDefaultValue("[]");
        config.setSortOrder(21);
        saveConfig(config);
    }

    private void ensureModelAliasesConfig(String group) {
        if (getByKeyAndGroup(MODEL_ALIASES_KEY, group) != null) {
            return;
        }
        SystemConfig config = new SystemConfig();
        config.setConfigKey(MODEL_ALIASES_KEY);
        config.setConfigGroup(group);
        config.setConfigLabel("模型自定义名称");
        config.setConfigValue("{}");
        config.setDefaultValue("{}");
        config.setSortOrder(20);
        systemConfigMapper.insert(config);
    }

    private void ensureHiddenModelsConfig(String group) {
        if (getByKeyAndGroup(HIDDEN_MODELS_KEY, group) != null) {
            return;
        }
        SystemConfig config = new SystemConfig();
        config.setConfigKey(HIDDEN_MODELS_KEY);
        config.setConfigGroup(group);
        config.setConfigLabel("前端隐藏模型");
        config.setConfigValue("[]");
        config.setDefaultValue("[]");
        config.setSortOrder(21);
        systemConfigMapper.insert(config);
    }

    private UiBrandingConfig readLegacyUiBrandingConfig() {
        String rawValue = getValue(UI_BRANDING_KEY, UI_CONFIG_GROUP, "");
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        try {
            return JSONUtil.toBean(rawValue, UiBrandingConfig.class);
        } catch (Exception ex) {
            log.warn("Invalid legacy UI branding config: {}", rawValue, ex);
            return null;
        }
    }

    private UiBrandingConfig normalizeUiBrandingConfig(UiBrandingConfig source) {
        UiBrandingConfig defaults = defaultUiBrandingConfig();
        UiBrandingConfig normalized = source == null ? new UiBrandingConfig() : source;

        if (normalized.getAppTitle() == null || normalized.getAppTitle().trim().isEmpty()) {
            normalized.setAppTitle(defaults.getAppTitle());
        } else {
            normalized.setAppTitle(normalized.getAppTitle().trim());
        }

        if (normalized.getLoginTitle() == null || normalized.getLoginTitle().trim().isEmpty()) {
            normalized.setLoginTitle(normalized.getAppTitle());
        } else {
            normalized.setLoginTitle(normalized.getLoginTitle().trim());
        }

        if (normalized.getLogoUrl() == null || normalized.getLogoUrl().trim().isEmpty()) {
            normalized.setLogoUrl(defaults.getLogoUrl());
        } else {
            normalized.setLogoUrl(normalized.getLogoUrl().trim());
        }

        if (normalized.getShowLogo() == null) {
            normalized.setShowLogo(Boolean.TRUE);
        }

        if (normalized.getFaviconUrl() == null || normalized.getFaviconUrl().trim().isEmpty()) {
            normalized.setFaviconUrl(defaults.getFaviconUrl());
        } else {
            normalized.setFaviconUrl(normalized.getFaviconUrl().trim());
        }

        if (normalized.getShowFavicon() == null) {
            normalized.setShowFavicon(Boolean.TRUE);
        }

        if (normalized.getLoginDefaultRoute() == null || normalized.getLoginDefaultRoute().trim().isEmpty()) {
            normalized.setLoginDefaultRoute("/manager/dataview");
        } else {
            normalized.setLoginDefaultRoute(normalized.getLoginDefaultRoute().trim());
        }

        List<UiMenuGroupConfig> menuGroups = new ArrayList<>();
        Map<String, String> defaultGroupTitles = new LinkedHashMap<>();
        for (UiMenuGroupConfig item : defaults.getMenuGroups()) {
            defaultGroupTitles.put(item.getKey(), item.getTitle());
        }

        Set<String> seenGroupKeys = new HashSet<>();
        if (normalized.getMenuGroups() != null) {
            for (UiMenuGroupConfig item : normalized.getMenuGroups()) {
                if (item == null || item.getKey() == null || item.getKey().trim().isEmpty()) {
                    continue;
                }
                String key = item.getKey().trim();
                if (!defaultGroupTitles.containsKey(key) || seenGroupKeys.contains(key)) {
                    continue;
                }
                UiMenuGroupConfig group = new UiMenuGroupConfig();
                group.setKey(key);
                String title = item.getTitle() == null || item.getTitle().trim().isEmpty()
                        ? defaultGroupTitles.get(key)
                        : item.getTitle().trim();
                group.setTitle(title);
                menuGroups.add(group);
                seenGroupKeys.add(key);
            }
        }
        for (UiMenuGroupConfig item : defaults.getMenuGroups()) {
            if (seenGroupKeys.contains(item.getKey())) {
                continue;
            }
            UiMenuGroupConfig group = new UiMenuGroupConfig();
            group.setKey(item.getKey());
            group.setTitle(item.getTitle());
            menuGroups.add(group);
        }
        normalized.setMenuGroups(menuGroups);

        List<UiRouteConfig> routes = new ArrayList<>();
        Map<String, UiRouteConfig> defaultRoutes = new LinkedHashMap<>();
        for (UiRouteConfig item : defaults.getRoutes()) {
            defaultRoutes.put(item.getPath(), item);
        }

        Set<String> seenRoutePaths = new HashSet<>();
        if (normalized.getRoutes() != null) {
            for (UiRouteConfig item : normalized.getRoutes()) {
                if (item == null || item.getPath() == null || item.getPath().trim().isEmpty()) {
                    continue;
                }
                String path = item.getPath().trim();
                UiRouteConfig defaultRoute = defaultRoutes.get(path);
                if (defaultRoute == null || seenRoutePaths.contains(path)) {
                    continue;
                }
                UiRouteConfig route = new UiRouteConfig();
                route.setPath(path);
                route.setTitle(item.getTitle() == null || item.getTitle().trim().isEmpty()
                        ? defaultRoute.getTitle()
                        : item.getTitle().trim());
                if ("/manager/config".equals(path)) {
                    route.setVisible(Boolean.TRUE);
                } else {
                    route.setVisible(item.getVisible() == null ? defaultRoute.getVisible() : item.getVisible());
                }
                routes.add(route);
                seenRoutePaths.add(path);
            }
        }
        for (UiRouteConfig item : defaults.getRoutes()) {
            if (seenRoutePaths.contains(item.getPath())) {
                continue;
            }
            UiRouteConfig route = new UiRouteConfig();
            route.setPath(item.getPath());
            route.setTitle(item.getTitle());
            route.setVisible(item.getVisible());
            routes.add(route);
        }
        normalized.setRoutes(routes);

        return normalized;
    }

    private UiBrandingConfig defaultUiBrandingConfig() {
        UiBrandingConfig config = new UiBrandingConfig();
        config.setAppTitle(DEFAULT_APP_TITLE);
        config.setLoginTitle(DEFAULT_APP_TITLE);
        config.setLogoUrl(DEFAULT_LOGO_URL);
        config.setShowLogo(Boolean.TRUE);
        config.setFaviconUrl(DEFAULT_LOGO_URL);
        config.setShowFavicon(Boolean.TRUE);
        config.setLoginDefaultRoute("/manager/dataview");

        List<UiMenuGroupConfig> groups = new ArrayList<>();
        groups.add(createMenuGroup("user", "用户管理"));
        groups.add(createMenuGroup("data", "数据管理"));
        groups.add(createMenuGroup("diagnosis", "智能风机辅助检测与分析"));
        config.setMenuGroups(groups);

        List<UiRouteConfig> routes = new ArrayList<>();
        routes.add(createRoute("/manager/admin", "管理员信息", true));
        routes.add(createRoute("/manager/config", "系统配置", true));
        routes.add(createRoute("/manager/detect", "风机缺陷检测", true));
        routes.add(createRoute("/manager/imagetovideo", "风机图像智能剪辑", true));
        routes.add(createRoute("/manager/video", "风机视频检测", true));
        routes.add(createRoute("/manager/history", "检测历史", true));
        routes.add(createRoute("/manager/dataview", "数据可视化", true));
        config.setRoutes(routes);

        return config;
    }

    private boolean getBooleanValue(String configKey, String configGroup, boolean defaultValue) {
        String value = getValue(configKey, configGroup);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private void saveUiConfigValue(String configKey, String label, String value, String defaultValue, int sortOrder) {
        SystemConfig uiConfig = new SystemConfig();
        uiConfig.setConfigKey(configKey);
        uiConfig.setConfigGroup(UI_CONFIG_GROUP);
        uiConfig.setConfigLabel(label);
        uiConfig.setConfigValue(value == null ? "" : value);
        uiConfig.setDefaultValue(defaultValue == null ? "" : defaultValue);
        uiConfig.setSortOrder(sortOrder);
        saveConfig(uiConfig);
    }

    private String menuGroupTitleKey(String groupKey) {
        return UI_MENU_GROUP_PREFIX + groupKey + "_title";
    }

    private String routeTitleKey(String path) {
        return UI_ROUTE_PREFIX + routeKey(path) + "_title";
    }

    private String routeVisibleKey(String path) {
        return UI_ROUTE_PREFIX + routeKey(path) + "_visible";
    }

    private String routeKey(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "unknown";
        }
        String normalized = path.trim().replace("\\", "/");
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private UiMenuGroupConfig createMenuGroup(String key, String title) {
        UiMenuGroupConfig group = new UiMenuGroupConfig();
        group.setKey(key);
        group.setTitle(title);
        return group;
    }

    private UiRouteConfig createRoute(String path, String title, boolean visible) {
        UiRouteConfig route = new UiRouteConfig();
        route.setPath(path);
        route.setTitle(title);
        route.setVisible(visible);
        return route;
    }

    private void collectModelFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectModelFiles(file, result);
            } else if (isSupportedModelFile(file)) {
                result.add(file);
            }
        }
    }

    private boolean isSupportedModelFile(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return false;
        }
        String extension = name.substring(dotIndex + 1).toLowerCase();
        return SUPPORTED_MODEL_EXTENSIONS.contains(extension);
    }

    private String extractModelLabel(File modelFile) {
        File weightsDir = modelFile.getParentFile();
        if (weightsDir == null) {
            return stripModelExtension(modelFile.getName());
        }
        File modelDir = weightsDir.getParentFile();
        String fileName = modelFile.getName();
        if (("best.pt".equalsIgnoreCase(fileName) || "last.pt".equalsIgnoreCase(fileName))
                && modelDir != null
                && "weights".equalsIgnoreCase(weightsDir.getName())) {
            return modelDir.getName();
        }
        return stripModelExtension(fileName);
    }

    private String stripModelExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }
}
