package com.example.springb.controller;

import com.example.springb.common.Result;
import com.example.springb.service.SystemConfigService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/ui")
public class PublicUiController {

    @Resource
    private SystemConfigService systemConfigService;

    @GetMapping("/branding")
    public Result getUiBranding() {
        return Result.success(systemConfigService.getUiBrandingConfig());
    }
}
