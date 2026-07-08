package com.observabilitymesh.sloprovisioner.prometheus;

import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class PrometheusReloader {

    private static final Logger log = LoggerFactory.getLogger(PrometheusReloader.class);

    private final String reloadUrl;
    private final HttpClient httpClient;

    public PrometheusReloader(SloProvisionerProperties properties) {
        this.reloadUrl = properties.prometheusReloadUrl();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public void reload() {
        if (reloadUrl == null || reloadUrl.isBlank()) {
            log.debug("prometheus reload skipped: no reload URL configured");
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(reloadUrl))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new PrometheusReloadException(
                        "prometheus reload failed with status " + response.statusCode() + ": " + response.body());
            }
            log.info("prometheus configuration reloaded");
        } catch (PrometheusReloadException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PrometheusReloadException("prometheus reload failed: " + ex.getMessage(), ex);
        }
    }
}
