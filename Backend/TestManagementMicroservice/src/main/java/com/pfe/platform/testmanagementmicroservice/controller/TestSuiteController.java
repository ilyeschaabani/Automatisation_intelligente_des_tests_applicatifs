package com.pfe.platform.testmanagementmicroservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Deprecated
@RestController
@RequestMapping("/api/suites")
public class TestSuiteController {

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    @ResponseStatus(HttpStatus.GONE)
    public void gone() {
        // TestSuite is removed. Use /api/projects and /api/cases?projectId=... instead.
    }
}
