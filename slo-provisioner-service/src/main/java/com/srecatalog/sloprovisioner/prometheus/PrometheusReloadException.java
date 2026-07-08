package com.srecatalog.sloprovisioner.prometheus;

public class PrometheusReloadException extends RuntimeException {

    public PrometheusReloadException(String message) {
        super(message);
    }

    public PrometheusReloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
