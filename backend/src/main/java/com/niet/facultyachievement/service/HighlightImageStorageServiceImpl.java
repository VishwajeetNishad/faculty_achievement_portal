package com.niet.facultyachievement.service;

import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.UUID;

@Service
public class HighlightImageStorageServiceImpl implements HighlightImageStorageService {

    /** Formats accepted, with the extension each one is stored under. */
    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";
    private static final String WEBP = "image/webp";

    /**
     * Nothing legitimate on a web page is bigger than this in either direction.
     * A file inside the size cap can still declare enormous dimensions, and the
     * browser — not this server — is what would try to allocate the bitmap. So
     * the header is sanity-checked before the file is accepted, and visitors are
     * never handed a decompression bomb an administrator uploaded by accident.
     */
    private static final int MAX_DIMENSION_PX = 10_000;

    private final Path storageLocation;
    private final long maxFileSize;

    public HighlightImageStorageServiceImpl(
            @Value("${app.highlight-storage.upload-dir:uploads/highlights}") String uploadDir,
            @Value("${app.highlight-storage.max-file-size:2097152}") long maxFileSize) {

        this.maxFileSize = maxFileSize;
        this.storageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.storageLocation);
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Could not create directory for highlight image storage: " + this.storageLocation, ex);
        }
    }

    @Override
    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No image file was submitted.");
        }

        if (file.getSize() > this.maxFileSize) {
            throw new BadRequestException("Image is too large. The limit is "
                    + (this.maxFileSize / 1024 / 1024) + " MB — please export the poster at a smaller file size.");
        }

        /* Read once into memory, then validate and measure the same bytes that
           get written. The alternative — reading the stream three times — leaves
           room for the header that was checked and the body that was stored to
           come from different reads. Bounded by the size cap above, so this can
           hold at most a couple of megabytes. */
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Could not read the uploaded image.");
        }

        // getSize() is metadata; this is the actual payload. Check both.
        if (bytes.length > this.maxFileSize) {
            throw new BadRequestException("Image is too large. The limit is "
                    + (this.maxFileSize / 1024 / 1024) + " MB.");
        }

        String contentType = detectContentType(bytes);
        if (contentType == null) {
            throw new BadRequestException(
                    "Only PNG, JPEG and WebP images are accepted. The file's own signature does not "
                            + "match any of those — renaming a file does not change its format.");
        }

        int[] dimensions = readDimensions(bytes, contentType);
        int width = dimensions[0];
        int height = dimensions[1];

        if (width <= 0 || height <= 0) {
            throw new BadRequestException("The image header is unreadable, so it may be corrupt. "
                    + "Please re-export the image and try again.");
        }
        if (width > MAX_DIMENSION_PX || height > MAX_DIMENSION_PX) {
            throw new BadRequestException("The image is " + width + " x " + height
                    + " pixels, which is far larger than a web banner needs. "
                    + "Please resize it to around 1600 pixels wide.");
        }

        String safeFilename = UUID.randomUUID() + extensionFor(contentType);

        try {
            Path target = this.storageLocation.resolve(safeFilename).normalize();

            /* Belt-and-braces. The name is a UUID this method just generated, so
               it cannot contain a traversal sequence — but the guard stays,
               because the day someone changes the naming scheme is the day it
               starts mattering. */
            if (!target.startsWith(this.storageLocation)) {
                throw new BadRequestException("Cannot store a file outside the highlights directory.");
            }

            Files.copy(new ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);

            return new StoredImage(safeFilename, contentType, bytes.length, width, height);

        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception ex) {
            throw new RuntimeException("Could not store highlight image " + safeFilename, ex);
        }
    }

    @Override
    public Resource loadAsResource(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new ResourceNotFoundException("Highlight image not found.");
        }
        try {
            Path filePath = this.storageLocation.resolve(filename).normalize();

            if (!filePath.startsWith(this.storageLocation)) {
                throw new BadRequestException("Path traversal attempt detected and blocked.");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new ResourceNotFoundException("Highlight image not found.");

        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("Highlight image not found.");
        }
    }

    @Override
    public void delete(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            Path filePath = this.storageLocation.resolve(filename).normalize();
            if (filePath.startsWith(this.storageLocation)) {
                Files.deleteIfExists(filePath);
            }
        } catch (Exception ex) {
            /* Deliberately swallowed. The database row is already gone, which is
               what the administrator asked for; an orphaned file on disk is a
               housekeeping matter, not a reason to fail their request. */
        }
    }

    /* ================================================================
       Format detection

       The submitted filename and Content-Type header are both supplied by
       the client and are trivially forged, so neither is consulted. Only
       the bytes decide.
       ================================================================ */

    private static String detectContentType(byte[] b) {
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (startsWith(b, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        // JPEG: FF D8 FF (SOI marker plus the first marker's prefix)
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        /* WebP is a RIFF container: "RIFF" at 0, then a 4-byte length, then
           "WEBP" at 8. Checking only "RIFF" would also accept a WAV file. */
        if (startsWith(b, 0, 0x52, 0x49, 0x46, 0x46)
                && startsWith(b, 8, 0x57, 0x45, 0x42, 0x50)) {
            return WEBP;
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case PNG -> ".png";
            case JPEG -> ".jpg";
            case WEBP -> ".webp";
            // Unreachable: detectContentType only returns the three above.
            default -> throw new BadRequestException("Unsupported image type.");
        };
    }

    /* ================================================================
       Dimensions

       Read from the header only — the pixel data is never decoded. That
       keeps a large-but-valid image from costing this server a full
       bitmap allocation just to learn how wide it is.
       ================================================================ */

    private static int[] readDimensions(byte[] bytes, String contentType) {
        /* WebP has to be parsed by hand. The JDK's ImageIO ships readers for
           JPEG, PNG, GIF, BMP, WBMP and TIFF — but NOT WebP, so
           getImageReaders() returns an empty iterator for it and there is no
           dimension to store. The alternatives were a third-party imaging
           dependency or dropping WebP support; the container format is short
           and precisely specified, so parsing the one header field we need is
           cheaper and adds nothing to the build. */
        if (WEBP.equals(contentType)) {
            return readWebpDimensions(bytes);
        }
        return readViaImageIo(bytes);
    }

    private static int[] readViaImageIo(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return new int[]{0, 0};
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return new int[]{0, 0};
            }
            ImageReader reader = readers.next();
            try {
                // false = do not buffer the whole image; only the header is parsed.
                reader.setInput(input, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            // A header this malformed is rejected by the caller's 0-check.
            return new int[]{0, 0};
        }
    }

    /**
     * Canvas size from a WebP container.
     *
     * <p>Three encodings exist and they store the size in three different
     * places, so all three are handled — a file rejected here for being
     * "unreadable" when it opens fine in a browser would be a confusing bug.
     *
     * <ul>
     *   <li>{@code VP8 } (simple lossy) — 14-bit width and height follow the
     *       three-byte start code {@code 9D 01 2A} at offset 23;</li>
     *   <li>{@code VP8L} (lossless) — 14-bit width-1 and height-1 packed into
     *       the 32 bits after the {@code 0x2F} signature byte;</li>
     *   <li>{@code VP8X} (extended: animation, alpha, ICC) — 24-bit canvas
     *       width-1 and height-1 at offset 24.</li>
     * </ul>
     */
    private static int[] readWebpDimensions(byte[] b) {
        if (b.length < 30) {
            return new int[]{0, 0};
        }
        String fourCc = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);

        switch (fourCc) {
            case "VP8 " -> {
                if (!startsWith(b, 23, 0x9D, 0x01, 0x2A)) {
                    return new int[]{0, 0};
                }
                int w = ((b[26] & 0xFF) | ((b[27] & 0xFF) << 8)) & 0x3FFF;
                int h = ((b[28] & 0xFF) | ((b[29] & 0xFF) << 8)) & 0x3FFF;
                return new int[]{w, h};
            }
            case "VP8L" -> {
                if ((b[20] & 0xFF) != 0x2F) {
                    return new int[]{0, 0};
                }
                int packed = (b[21] & 0xFF)
                        | ((b[22] & 0xFF) << 8)
                        | ((b[23] & 0xFF) << 16)
                        | ((b[24] & 0xFF) << 24);
                int w = (packed & 0x3FFF) + 1;
                int h = ((packed >>> 14) & 0x3FFF) + 1;
                return new int[]{w, h};
            }
            case "VP8X" -> {
                int w = ((b[24] & 0xFF) | ((b[25] & 0xFF) << 8) | ((b[26] & 0xFF) << 16)) + 1;
                int h = ((b[27] & 0xFF) | ((b[28] & 0xFF) << 8) | ((b[29] & 0xFF) << 16)) + 1;
                return new int[]{w, h};
            }
            default -> {
                return new int[]{0, 0};
            }
        }
    }
}
