package ieti.jobswipe.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import ieti.jobswipe.service.DocumentStorageService;

import ieti.jobswipe.service.DocumentStorageService;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class DocumentStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreDocumentLocally() throws Exception {
        DocumentStorageService service = new DocumentStorageService(
                "local",
                tempDir.toString(),
                "",
                "us-east-1",
                "jobswipe/uploads",
                "");
        MockMultipartFile file = new MockMultipartFile(
                "document",
                "resume.pdf",
                "application/pdf",
                "cv-content".getBytes());

        DocumentStorageService.StoredDocument storedDocument = service.store(file);

        assertEquals("resume.pdf", storedDocument.originalFilename());
        assertEquals("application/pdf", storedDocument.contentType());
        assertEquals(file.getSize(), storedDocument.size());
        assertTrue(storedDocument.storageFilename().endsWith(".pdf"));
        assertTrue(Files.exists(Path.of(storedDocument.storagePath())));
    }

    @Test
    void shouldRejectEmptyDocument() {
        DocumentStorageService service = new DocumentStorageService(
                "local",
                tempDir.toString(),
                "",
                "us-east-1",
                "jobswipe/uploads",
                "");
        MockMultipartFile file = new MockMultipartFile("document", "resume.pdf", "application/pdf", new byte[0]);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.store(file));

        assertEquals("Document is empty", exception.getMessage());
    }

    @Test
    void shouldRejectNullDocument() {
        DocumentStorageService service = new DocumentStorageService(
                "local",
                tempDir.toString(),
                "",
                "us-east-1",
                "jobswipe/uploads",
                "");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.store(null));

        assertEquals("Document is empty", exception.getMessage());
    }

    @Test
    void shouldRejectOversizedDocument() {
        DocumentStorageService service = new DocumentStorageService(
                "local",
                tempDir.toString(),
                "",
                "us-east-1",
                "jobswipe/uploads",
                "");
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((5L * 1024L * 1024L) + 1L);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.store(file));

        assertEquals("Document exceeds 5MB size limit", exception.getMessage());
    }

    @Test
    void shouldRejectUnsupportedExtension() {
        DocumentStorageService service = new DocumentStorageService(
                "local",
                tempDir.toString(),
                "",
                "us-east-1",
                "jobswipe/uploads",
                "");
        MockMultipartFile file = new MockMultipartFile("document", "malware.exe", "application/octet-stream", "bad".getBytes());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.store(file));

        assertEquals("Document type is not allowed", exception.getMessage());
    }

    @Test
    void shouldRequireS3BucketWhenProviderIsS3() {
        DocumentStorageService service = new DocumentStorageService(
                "s3",
                tempDir.toString(),
                " ",
                "us-east-1",
                "/jobswipe/uploads/",
                "https://cdn.example.com/");
        MockMultipartFile file = new MockMultipartFile("document", "resume.pdf", "application/pdf", "cv-content".getBytes());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.store(file));

        assertEquals("S3 bucket is not configured", exception.getMessage());
    }

    @Test
    void shouldRequireS3BucketWhenBucketIsNull() {
        DocumentStorageService service = new DocumentStorageService(
                "s3",
                tempDir.toString(),
                null,
                "us-east-1",
                "/jobswipe/uploads/",
                "https://cdn.example.com/");
        MockMultipartFile file = new MockMultipartFile("document", "resume.pdf", "application/pdf", "cv-content".getBytes());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.store(file));

        assertEquals("S3 bucket is not configured", exception.getMessage());
    }

    @Test
    void shouldStoreDocumentInS3WhenConfigured() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        DocumentStorageService service = new DocumentStorageService(
                "s3",
                tempDir.toString(),
                "bucket-name",
                "us-east-1",
                "   ",
                "https://cdn.example.com/") {
            @Override
            protected S3Client createS3Client() {
                return s3Client;
            }
        };

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(200L);
        when(file.getOriginalFilename()).thenReturn("resume.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getBytes()).thenReturn("pdf-content".getBytes());

        DocumentStorageService.StoredDocument stored = service.store(file);

        assertEquals("resume.pdf", stored.originalFilename());
        assertEquals("application/pdf", stored.contentType());
        assertEquals(200L, stored.size());
        assertTrue(stored.storageFilename().endsWith(".pdf"));
        assertEquals("https://cdn.example.com/" + stored.storageFilename(), stored.storagePath());

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals("bucket-name", requestCaptor.getValue().bucket());
        assertEquals(stored.storageFilename(), requestCaptor.getValue().key());
        assertFalse(requestCaptor.getValue().key().contains("/"));
    }

    @Test
    void shouldWrapIOExceptionWhenS3ReadFails() throws Exception {
        DocumentStorageService service = new DocumentStorageService(
                "s3",
                tempDir.toString(),
                "bucket-name",
                "us-east-1",
                "/jobswipe/uploads/",
                "https://cdn.example.com/");
        MultipartFile file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("resume.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getBytes()).thenThrow(new IOException("boom"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.store(file));

        assertEquals("Failed to store document", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void shouldWrapIOExceptionWhenLocalStorageFails() throws Exception {
        DocumentStorageService service = new DocumentStorageService(
                "local",
                tempDir.toString(),
                "",
                "us-east-1",
                "jobswipe/uploads",
                "");
        MultipartFile file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L);
        when(file.getOriginalFilename()).thenReturn("resume.pdf");
        when(file.getInputStream()).thenThrow(new IOException("boom"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.store(file));

        assertEquals("Failed to store document", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void shouldNormalizePrefixAndBuildS3Path() throws Exception {
        DocumentStorageService service = new DocumentStorageService(
                "s3",
                tempDir.toString(),
                "bucket-name",
                "us-east-1",
                "/jobswipe/uploads/",
                "https://cdn.example.com/");

        Method normalizePrefix = DocumentStorageService.class.getDeclaredMethod("normalizePrefix", String.class);
        normalizePrefix.setAccessible(true);
        String normalized = (String) normalizePrefix.invoke(service, "/jobswipe/uploads/");

        Method buildS3StoragePath = DocumentStorageService.class.getDeclaredMethod("buildS3StoragePath", String.class);
        buildS3StoragePath.setAccessible(true);
        String storagePath = (String) buildS3StoragePath.invoke(service, "jobswipe/uploads/file.pdf");

        Method extractExtension = DocumentStorageService.class.getDeclaredMethod("extractExtension", String.class);
        extractExtension.setAccessible(true);
        String extension = (String) extractExtension.invoke(service, "Resume.PDF");

        assertEquals("jobswipe/uploads", normalized);
        assertEquals("https://cdn.example.com/jobswipe/uploads/file.pdf", storagePath);
        assertEquals("pdf", extension);
    }

    @Test
    void shouldBuildS3SchemePathWhenPublicBaseUrlIsMissing() throws Exception {
        DocumentStorageService service = new DocumentStorageService(
                "s3",
                tempDir.toString(),
                "bucket-name",
                "us-east-1",
                "jobswipe/uploads",
                "");

        Method buildS3StoragePath = DocumentStorageService.class.getDeclaredMethod("buildS3StoragePath", String.class);
        buildS3StoragePath.setAccessible(true);
        String storagePath = (String) buildS3StoragePath.invoke(service, "jobswipe/uploads/file.pdf");

        assertEquals("s3://bucket-name/jobswipe/uploads/file.pdf", storagePath);
    }

    @Test
    void shouldBuildS3HttpPathWhenPublicBaseUrlHasNoTrailingSlash() throws Exception {
        DocumentStorageService service = new DocumentStorageService(
                "s3",
                tempDir.toString(),
                "bucket-name",
                "us-east-1",
                "jobswipe/uploads",
                "https://cdn.example.com");

        Method buildS3StoragePath = DocumentStorageService.class.getDeclaredMethod("buildS3StoragePath", String.class);
        buildS3StoragePath.setAccessible(true);
        String storagePath = (String) buildS3StoragePath.invoke(service, "jobswipe/uploads/file.pdf");

        assertEquals("https://cdn.example.com/jobswipe/uploads/file.pdf", storagePath);
    }

    @Test
    void shouldRejectWhenOriginalFilenameIsNullAndExtensionCannotBeExtracted() {
        DocumentStorageService service = new DocumentStorageService(
                "local",
                tempDir.toString(),
                "",
                "us-east-1",
                "jobswipe/uploads",
                "");
        MultipartFile file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(200L);
        when(file.getOriginalFilename()).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.store(file));

        assertEquals("Document type is not allowed", exception.getMessage());
    }

    @Test
    void shouldHandleBlankPrefixAndMissingExtension() throws Exception {
        DocumentStorageService service = new DocumentStorageService(
                "local",
                tempDir.toString(),
                "",
                "us-east-1",
                "   ",
                "");

        Method normalizePrefix = DocumentStorageService.class.getDeclaredMethod("normalizePrefix", String.class);
        normalizePrefix.setAccessible(true);
        String normalized = (String) normalizePrefix.invoke(service, "   ");

        Method extractExtension = DocumentStorageService.class.getDeclaredMethod("extractExtension", String.class);
        extractExtension.setAccessible(true);
        String extensionWithoutDot = (String) extractExtension.invoke(service, "README");
        String extensionTrailingDot = (String) extractExtension.invoke(service, "README.");

        assertEquals("", normalized);
        assertEquals("", extensionWithoutDot);
        assertEquals("", extensionTrailingDot);
    }

    @Test
    void shouldNormalizeNullPrefixAsEmpty() throws Exception {
        DocumentStorageService service = new DocumentStorageService(
                "local",
                tempDir.toString(),
                "",
                "us-east-1",
                "jobswipe/uploads",
                "");

        Method normalizePrefix = DocumentStorageService.class.getDeclaredMethod("normalizePrefix", String.class);
        normalizePrefix.setAccessible(true);
        String normalized = (String) normalizePrefix.invoke(service, new Object[] { null });

        assertEquals("", normalized);
    }

    @Test
    void shouldBuildS3SchemePathWhenPublicBaseUrlIsNull() throws Exception {
        DocumentStorageService service = new DocumentStorageService(
                "s3",
                tempDir.toString(),
                "bucket-name",
                "us-east-1",
                "jobswipe/uploads",
                null);

        Method buildS3StoragePath = DocumentStorageService.class.getDeclaredMethod("buildS3StoragePath", String.class);
        buildS3StoragePath.setAccessible(true);
        String storagePath = (String) buildS3StoragePath.invoke(service, "jobswipe/uploads/file.pdf");

        assertEquals("s3://bucket-name/jobswipe/uploads/file.pdf", storagePath);
    }

    @Test
    void shouldCreateS3Client() {
        DocumentStorageService service = new DocumentStorageService(
                "s3",
                tempDir.toString(),
                "bucket-name",
                "us-east-1",
                "jobswipe/uploads",
                "https://cdn.example.com");

        try (S3Client s3Client = service.createS3Client()) {
            assertNotNull(s3Client);
        }
    }
}

