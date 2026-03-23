package com.pfe.platform.apiscannerservice.aicode.model;

import java.nio.file.Path;

public record IngestedFile(Path path, String content) {
}

