package com.example.springb.entity;

import java.util.ArrayList;
import java.util.List;

public class UiBrandingConfig {

    private String appTitle;
    private String loginTitle;
    private String logoUrl;
    private Boolean showLogo;
    private String faviconUrl;
    private Boolean showFavicon;
    private String loginDefaultRoute;
    private List<UiMenuGroupConfig> menuGroups = new ArrayList<>();
    private List<UiRouteConfig> routes = new ArrayList<>();

    public String getAppTitle() {
        return appTitle;
    }

    public void setAppTitle(String appTitle) {
        this.appTitle = appTitle;
    }

    public String getLoginTitle() {
        return loginTitle;
    }

    public void setLoginTitle(String loginTitle) {
        this.loginTitle = loginTitle;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public Boolean getShowLogo() {
        return showLogo;
    }

    public void setShowLogo(Boolean showLogo) {
        this.showLogo = showLogo;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public Boolean getShowFavicon() {
        return showFavicon;
    }

    public void setShowFavicon(Boolean showFavicon) {
        this.showFavicon = showFavicon;
    }

    public String getLoginDefaultRoute() {
        return loginDefaultRoute;
    }

    public void setLoginDefaultRoute(String loginDefaultRoute) {
        this.loginDefaultRoute = loginDefaultRoute;
    }

    public List<UiMenuGroupConfig> getMenuGroups() {
        return menuGroups;
    }

    public void setMenuGroups(List<UiMenuGroupConfig> menuGroups) {
        this.menuGroups = menuGroups;
    }

    public List<UiRouteConfig> getRoutes() {
        return routes;
    }

    public void setRoutes(List<UiRouteConfig> routes) {
        this.routes = routes;
    }
}
