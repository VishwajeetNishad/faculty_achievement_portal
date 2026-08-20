package com.niet.facultyachievement.service;

import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path fileStorageLocation;
    private final long maxFileSize;

    public FileStorageServiceImpl(
            @Value("${app.file-storage.upload-dir:uploads/achievements}") String uploadDir,
            @Value("${app.file-storage.max-file-size:10485760}") long maxFileSize) {
        
        this.maxFileSize = maxFileSize;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create directory for upload storage: " + this.fileStorageLocation, ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Failed to store empty file.");
        }

        if (file.getSize() > this.maxFileSize) {
            throw new BadRequestException("File size exceeds maximum permitted limit of 10 MB.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new BadRequestException("Only PDF files (.pdf) are permitted for achievement proof documents.");
        }

        // Validate MIME type if provided
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && 
            !contentType.equalsIgnoreCase("application/pdf") && 
            !contentType.equalsIgnoreCase("application/octet-stream")) {
            throw new BadRequestException("Invalid content type: Must be application/pdf.");
        }

        // Deep Inspection: Validate PDF Magic Bytes (%PDF -> 0x25, 0x50, 0x44, 0x46)
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[4];
            int bytesRead = inputStream.read(header, 0, 4);
            if (bytesRead < 4 || 
                header[0] != 0x25 || // '%'
                header[1] != 0x50 || // 'P'
                header[2] != 0x44 || // 'D'
                header[3] != 0x46) { // 'F'
                throw new BadRequestException("Invalid PDF document: File magic header bytes do not match a valid PDF document.");
            }
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception ex) {
            throw new BadRequestException("Could not read file stream to validate PDF signature.");
        }

        // Generate safe unique filename using random UUID to completely eliminate Path Traversal risks
        String safeFilename = UUID.randomUUID().toString() + ".pdf";

        try {
            Path targetLocation = this.fileStorageLocation.resolve(safeFilename).normalize();
            
            // Path Traversal Security Check: Verify target path remains strictly inside upload directory
            if (!targetLocation.startsWith(this.fileStorageLocation)) {
                throw new BadRequestException("Cannot store file outside current storage directory.");
            }

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return safeFilename;

        } catch (Exception ex) {
            throw new RuntimeException("Could not store file " + safeFilename + ". Please try again!", ex);
        }
    }

    @Override
    public Resource loadFileAsResource(String filename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            
            // Strict Path Traversal Defense: Ensure requested file path is inside configured upload root
            if (!filePath.startsWith(this.fileStorageLocation)) {
                throw new BadRequestException("Path traversal attempt detected and blocked.");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Proof document file not found: " + filename);
            }
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("Proof document file not found: " + filename);
        }
    }

    @Override
    public void deleteFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return;
        }
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            if (filePath.startsWith(this.fileStorageLocation)) {
                Files.deleteIfExists(filePath);
            }
        } catch (Exception ex) {
            // Log & ignore error on physical cleanup
        }
    }
}
