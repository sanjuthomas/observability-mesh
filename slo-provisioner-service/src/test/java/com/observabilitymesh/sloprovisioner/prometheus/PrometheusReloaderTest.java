package com.observabilitymesh.sloprovisioner.prometheus;

import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrometheusReloaderTest {

    @Test
    void skipsReloadWhenUrlBlank() {
        PrometheusReloader reloader = new PrometheusReloader(properties(""));

        assertThatCode(reloader::reload).doesNotThrowAnyException();
    }

    @Test
    void reloadSucceedsForHealthyEndpoint() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/-/reload", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            PrometheusReloader reloader = new PrometheusReloader(
                    properties("http://127.0.0.1:" + port + "/-/reload"));
            assertThatCode(reloader::reload).doesNotThrowAnyException();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsForNonSuccessStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/-/reload", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            PrometheusReloader reloader = new PrometheusReloader(
                    properties("http://127.0.0.1:" + port + "/-/reload"));
            assertThatThrownBy(reloader::reload).isInstanceOf(PrometheusReloadException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsForUnreachableEndpoint() {
        PrometheusReloader reloader = new PrometheusReloader(properties("http://127.0.0.1:1/-/reload"));

        assertThatThrownBy(reloader::reload).isInstanceOf(PrometheusReloadException.class);
    }

    private static SloProvisionerProperties properties(String reloadUrl) {
        return new SloProvisionerProperties(
                60_000, "service-level-objectives", "slo-provision-state",
                "/rules", "_archive", reloadUrl, "sloth", "/work", "payment-prometheus");
    }
}
