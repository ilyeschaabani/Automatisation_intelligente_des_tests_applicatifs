package com.example.ressourcemicroservice.Service;


        import com.example.ressourcemicroservice.Entity.Enumeration.TypeRessource;
        import org.springframework.stereotype.Service;
        import org.springframework.web.multipart.MultipartFile;

        import java.io.IOException;
        import java.nio.file.*;
        import java.util.UUID;

@Service
public class FileStorageService {


    public String uploadFile(MultipartFile file, TypeRessource type) throws IOException {
        // Define the base upload directory inside the microservice
        String uploadDir = System.getProperty("user.dir") + "/src/main/resources/uploads/" + type.toString().toLowerCase();
        Path storagePath = Paths.get(uploadDir);

        // Ensure directory exists
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
        }

        // Generate a unique filename
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String uniqueFileName = UUID.randomUUID().toString() + "." + fileExtension;

        // Save the file physically
        Path filePath = storagePath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);


        return uniqueFileName;
    }

    private String getFileExtension(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
