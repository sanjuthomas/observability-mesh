package com.observabilitymesh.sloauthor.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UiController {

    private static final HttpHeaders NO_CACHE = new HttpHeaders();

    static {
        NO_CACHE.setCacheControl("no-cache");
    }

    @GetMapping(value = {"/ui", "/ui/"})
    public ResponseEntity<ClassPathResource> index() {
        return ResponseEntity.ok()
            .headers(NO_CACHE)
            .contentType(MediaType.TEXT_HTML)
            .body(new ClassPathResource("static/index.html"));
    }
}
