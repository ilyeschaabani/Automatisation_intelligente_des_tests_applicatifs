package com.example.ressourcemicroservice.Controller; // Package corrigé

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.ressourcemicroservice.Entity.Enumeration.TypeRessource;
import com.example.ressourcemicroservice.Service.FileStorageService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
 public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }
    // Upload d'un fichier avec catégorisation      *
    @PostMapping("/{typeRessource}")
    public ResponseEntity<?> uploadResource(@PathVariable TypeRessource typeRessource, @RequestParam("file") MultipartFile file) throws IOException {
        // Convert fileType string to enum (handling case sensitivity)

        String fileName = fileStorageService.uploadFile(file, typeRessource);

        Map<String, String> response = new HashMap<>();
        response.put("message", "File uploaded successfully!");
        response.put("fileName", fileName);
        response.put("filePath", "uploads/" + typeRessource.toString().toLowerCase() + "/" + fileName);

        return ResponseEntity.ok(response);

    }
}
