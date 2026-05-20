package com.lms.www.leadmanagement.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path root = Paths.get("uploads");

    public FileStorageService() {
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize folder for upload!");
        }
    }

    public String save(MultipartFile file, String subDir) {
        try {
            Path targetDir = root.resolve(subDir);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            
            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Files.copy(file.getInputStream(), targetDir.resolve(filename));
            
            return "/uploads/" + subDir + "/" + filename;
        } catch (Exception e) {
            throw new RuntimeException("Could not store the file. Error: " + e.getMessage());
        }
    }
    public String saveCertificate(String certificateId, byte[] data) {
        try {
            Path targetDir = root.resolve("certificates");
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            String filename = certificateId + ".pdf";
            Path filePath = targetDir.resolve(filename);
            Files.write(filePath, data);
            return "certificates/" + filename;
        } catch (Exception e) {
            throw new RuntimeException("Could not store the certificate. Error: " + e.getMessage());
        }
    }

    public byte[] getFile(String storagePath) {
        try {
            String cleanPath = storagePath;
            if (cleanPath.startsWith("/uploads/")) {
                cleanPath = cleanPath.substring("/uploads/".length());
            } else if (cleanPath.startsWith("uploads/")) {
                cleanPath = cleanPath.substring("uploads/".length());
            }
            Path filePath = root.resolve(cleanPath);
            return Files.readAllBytes(filePath);
        } catch (Exception e) {
            throw new RuntimeException("Could not read the file. Error: " + e.getMessage());
        }
    }
}
