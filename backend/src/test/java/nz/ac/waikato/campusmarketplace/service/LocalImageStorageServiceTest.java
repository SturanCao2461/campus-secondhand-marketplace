package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalImageStorageServiceTest {

    @TempDir Path tempDir;
    LocalImageStorageService svc;

    @BeforeEach
    void setUp() {
        svc = new LocalImageStorageService(tempDir.toString());
    }

    private byte[] validJpeg() throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private byte[] validPng() throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void storeValidJpegReturnsRelativePath() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", validJpeg());

        String path = svc.store(file);

        assertTrue(path.startsWith("listings/"));
        assertTrue(path.endsWith(".jpg"));
        assertTrue(Files.exists(tempDir.resolve(path)));
    }

    @Test
    void storeValidPngReturnsRelativePath() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "image", "photo.png", "image/png", validPng());

        String path = svc.store(file);

        assertTrue(path.startsWith("listings/"));
        assertTrue(path.endsWith(".png"));
        assertTrue(Files.exists(tempDir.resolve(path)));
    }

    @Test
    void storeNullFileThrowsMissingImage() {
        ApiException ex = assertThrows(ApiException.class, () -> svc.store(null));
        assertEquals(ErrorCode.MISSING_IMAGE, ex.getCode());
    }

    @Test
    void storeEmptyFileThrowsMissingImage() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]);

        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals(ErrorCode.MISSING_IMAGE, ex.getCode());
    }

    @Test
    void storeOversizedFileThrowsInvalidImage() throws IOException {
        byte[] big = new byte[(int) (5 * 1024 * 1024 + 1)];
        System.arraycopy(validJpeg(), 0, big, 0, Math.min(validJpeg().length, big.length));
        MockMultipartFile file = new MockMultipartFile(
                "image", "big.jpg", "image/jpeg", big);

        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals(ErrorCode.INVALID_IMAGE, ex.getCode());
    }

    @Test
    void storeDisallowedContentTypeThrowsInvalidImage() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "image", "doc.pdf", "application/pdf", validJpeg());

        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals(ErrorCode.INVALID_IMAGE, ex.getCode());
    }

    @Test
    void storeMismatchedMagicBytesThrowsInvalidImage() {
        // Claims to be JPEG but bytes are random garbage
        byte[] garbage = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};
        MockMultipartFile file = new MockMultipartFile(
                "image", "fake.jpg", "image/jpeg", garbage);

        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals(ErrorCode.INVALID_IMAGE, ex.getCode());
    }

    @Test
    void storeCorruptedJpegHeaderButNotDecodableThrowsInvalidImage() {
        // Valid JPEG magic bytes but rest is garbage -> ImageIO.read returns null
        byte[] corrupt = new byte[1024];
        corrupt[0] = (byte) 0xFF;
        corrupt[1] = (byte) 0xD8;
        corrupt[2] = (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile(
                "image", "corrupt.jpg", "image/jpeg", corrupt);

        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals(ErrorCode.INVALID_IMAGE, ex.getCode());
    }

    @Test
    void loadExistingFileReturnsResource() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", validJpeg());
        String path = svc.store(file);

        Resource resource = svc.load(path);

        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    @Test
    void loadPathTraversalThrowsNotFound() {
        ApiException ex = assertThrows(ApiException.class,
                () -> svc.load("../../../etc/passwd"));
        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.getCode());
    }

    @Test
    void loadNonExistentFileThrowsNotFound() {
        ApiException ex = assertThrows(ApiException.class,
                () -> svc.load("listings/does-not-exist.jpg"));
        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.getCode());
    }
}
