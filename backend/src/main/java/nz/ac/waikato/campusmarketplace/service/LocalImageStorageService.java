package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalImageStorageService implements ImageStorageService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadRoot;

    public LocalImageStorageService(@Value("${app.upload.root}") String uploadRoot) {
        this.uploadRoot = Path.of(uploadRoot);
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.MISSING_IMAGE, "Image is required.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "Image must be under 5 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(ErrorCode.INVALID_IMAGE,
                    "Image must be JPEG, PNG, or WebP.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "Failed to read image data.");
        }

        ImageFormat format = detectFormat(bytes);
        if (format == null) {
            throw new ApiException(ErrorCode.INVALID_IMAGE,
                    "Image content does not match a supported format (JPEG/PNG/WebP).");
        }

        if (!isDecodableImage(bytes)) {
            throw new ApiException(ErrorCode.INVALID_IMAGE,
                    "Image file appears corrupted and cannot be decoded.");
        }

        String filename = UUID.randomUUID() + format.extension;
        String relativePath = "listings/" + filename;
        Path target = uploadRoot.resolve(relativePath);

        try {
            Files.createDirectories(target.getParent());
            Files.copy(new ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "Failed to save image to disk.");
        }

        return relativePath;
    }

    @Override
    public Resource load(String relativePath) {
        if (relativePath.contains("..") || relativePath.contains("\\")) {
            throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Invalid image path.");
        }
        try {
            Path file = uploadRoot.resolve(relativePath).normalize();
            if (!file.toRealPath().startsWith(uploadRoot.toRealPath())) {
                throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Invalid image path.");
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Image not found.");
            }
            return resource;
        } catch (IOException e) {
            throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Image not found.");
        }
    }

    private enum ImageFormat {
        JPEG(".jpg"), PNG(".png"), WEBP(".webp");
        final String extension;
        ImageFormat(String ext) { this.extension = ext; }
    }

    private ImageFormat detectFormat(byte[] bytes) {
        if (bytes.length < 12) return null;
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return ImageFormat.JPEG;
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return ImageFormat.PNG;
        }
        // WebP: RIFF????WEBP
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return ImageFormat.WEBP;
        }
        return null;
    }

    private boolean isDecodableImage(byte[] bytes) {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(is);
            return img != null;
        } catch (IOException e) {
            return false;
        }
    }
}
