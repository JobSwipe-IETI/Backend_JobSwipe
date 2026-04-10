package ieti.JobSwipe.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
