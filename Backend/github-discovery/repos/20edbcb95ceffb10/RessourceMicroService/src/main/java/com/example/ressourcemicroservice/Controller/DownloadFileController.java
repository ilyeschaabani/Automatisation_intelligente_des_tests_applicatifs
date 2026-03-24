package com.example.ressourcemicroservice.Controller;
        import org.springframework.core.io.Resource;
        import org.springframework.core.io.UrlResource;
        import org.springframework.http.HttpHeaders;
        import org.springframework.http.MediaType;
        import org.springframework.http.ResponseEntity;
        import org.springframework.web.bind.annotation.GetMapping;
        import org.springframework.web.bind.annotation.PathVariable;
        import org.springframework.web.bind.annotation.RequestMapping;
        import org.springframework.web.bind.annotation.RestController;

        import java.io.IOException;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.Paths;

@RestController
@RequestMapping("/api/download")
public class DownloadFileController {
    @GetMapping("/{type}/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String type, @PathVariable String fileName) throws IOException {
        System.out.println("im in download file ");
        // Chemin du dossier "uploads" dans le projet
        String uploadDir = System.getProperty("user.dir") + "/src/main/resources/uploads/";

        Path filePath = Paths.get(uploadDir + type + "/" + fileName);
        Resource resource = new UrlResource(filePath.toUri());

        if (resource.exists() && resource.isReadable()) {
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } else {
            throw new IOException("Could not read the file!");
        }
    }
}
