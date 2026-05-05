package ro.unibuc.prodeng.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ro.unibuc.prodeng.metrics.AppMetricsService;

@RestController
public class InfoController {

    private final AppMetricsService appMetricsService;

    public InfoController(AppMetricsService appMetricsService) {
        this.appMetricsService = appMetricsService;
    }

    @GetMapping("/info")
    public String info() {
        appMetricsService.incrementInfoCount();
        return "OK";
    }
}
