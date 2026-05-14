package nz.ac.waikato.campusmarketplace.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    /**
     * Validates and stores the uploaded image.
     * @return relative path (e.g. "listings/abc-123.jpg")
     */
    String store(MultipartFile file);

    /** Resolves a stored image to a Resource for serving. */
    Resource load(String relativePath);
}
