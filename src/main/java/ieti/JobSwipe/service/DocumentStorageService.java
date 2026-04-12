package ieti.JobSwipe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt");
    private static final long MAX_SIZE_BYTES = 5L * 1024L * 1024L;

    private final String storageProvider;
    private final Path uploadsPath;
    private final String s3Bucket;
    private final String s3Region;
    private final String s3Prefix;
    private final String s3PublicBaseUrl;

    public DocumentStorageService(
            @Value("${app.storage.provider:local}") String storageProvider,
            @Value("${app.uploads-dir:uploads}") String uploadsDir,
            @Value("${app.s3.bucket:}") String s3Bucket,
            @Value("${app.s3.region:us-east-1}") String s3Region,
            @Value("${app.s3.prefix:jobswipe/uploads}") String s3Prefix,
            @Value("${app.s3.public-base-url:}") String s3PublicBaseUrl) {
        this.storageProvider = storageProvider;
        this.uploadsPath = Path.of(uploadsDir).toAbsolutePath().normalize();
        this.s3Bucket = s3Bucket;
        this.s3Region = s3Region;
        this.s3Prefix = s3Prefix;
        this.s3PublicBaseUrl = s3PublicBaseUrl;
    }

    public StoredDocument store(MultipartFile document) {
        if (document == null || document.isEmpty()) {
            throw new IllegalArgumentException("Document is empty");
        }

        if (document.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Document exceeds 5MB size limit");
        }

        String originalFilename = document.getOriginalFilename() != null ? document.getOriginalFilename() : "document";
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Document type is not allowed");
        }

        String storageFilename = UUID.randomUUID() + "." + extension;

        if ("s3".equalsIgnoreCase(storageProvider)) {
            return storeToS3(document, originalFilename, storageFilename);
        }

        return storeToLocal(document, originalFilename, storageFilename);
    }

    private StoredDocument storeToLocal(MultipartFile document, String originalFilename, String storageFilename) {
        try {
            Files.createDirectories(uploadsPath);
            Path destination = uploadsPath.resolve(storageFilename);
            Files.copy(document.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return new StoredDocument(
                    originalFilename,
                    storageFilename,
                    destination.toString(),
                    document.getContentType(),
                    document.getSize());
        } catch (IOException exception) {
            throw new RuntimeException("Failed to store document", exception);
        }
    }

    private StoredDocument storeToS3(MultipartFile document, String originalFilename, String storageFilename) {
        if (s3Bucket == null || s3Bucket.isBlank()) {
            throw new IllegalArgumentException("S3 bucket is not configured");
        }

        String normalizedPrefix = normalizePrefix(s3Prefix);
        String objectKey = normalizedPrefix.isEmpty() ? storageFilename : normalizedPrefix + "/" + storageFilename;

        try {
            byte[] bytes = document.getBytes();

            try (S3Client s3Client = createS3Client()) {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(objectKey)
                        .contentType(document.getContentType())
                        .build();
                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));
            }

            String storagePath = buildS3StoragePath(objectKey);
            return new StoredDocument(
                    originalFilename,
                    storageFilename,
                    storagePath,
                    document.getContentType(),
                    document.getSize());
        } catch (IOException exception) {
            throw new RuntimeException("Failed to store document", exception);
        }
    }

    protected S3Client createS3Client() {
        return S3Client.builder()
                .region(Region.of(s3Region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String normalized = prefix.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String buildS3StoragePath(String objectKey) {
        if (s3PublicBaseUrl != null && !s3PublicBaseUrl.isBlank()) {
            String base = s3PublicBaseUrl.endsWith("/")
                    ? s3PublicBaseUrl.substring(0, s3PublicBaseUrl.length() - 1)
                    : s3PublicBaseUrl;
            return base + "/" + objectKey;
        }
        return "s3://" + s3Bucket + "/" + objectKey;
    }

    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredDocument(
            String originalFilename,
            String storageFilename,
            String storagePath,
            String contentType,
            long size) {
    }
}
