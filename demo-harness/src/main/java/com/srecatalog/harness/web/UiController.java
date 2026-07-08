package com.srecatalog.harness.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {

    @GetMapping({"/", "/index.html"})
    public String index() {
        return "forward:/static/index.html";
    }
}
