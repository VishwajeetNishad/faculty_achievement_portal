package com.niet.facultyachievement.service;

import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Upload validation for homepage highlight images.
 *
 * <p><strong>Why this file exists.</strong> This is the one place in the portal
 * where an administrator hands the server an arbitrary binary and the server
 * writes it to disk and later streams it back to every anonymous visitor. Three
 * things must hold, and only a test can keep them holding:
 *
 * <ol>
 *   <li>the <em>bytes</em> decide what the file is, never the filename or the
 *       browser-supplied {@code Content-Type} — both are attacker-controlled;</li>
 *   <li>nothing can be written outside, or read from outside, the highlights
 *       directory;</li>
 *   <li>the size cap is enforced against the real payload, not against the
 *       {@code getSize()} figure the client claims.</li>
 * </ol>
 *
 * <p><strong>No Spring, no database.</strong>
 * {@link HighlightImageStorageServiceImpl} takes its two settings as plain
 * constructor arguments, so it is built here directly against a JUnit
 * {@link TempDir}. That keeps {@code mvn test} runnable with no MySQL and no
 * application context, matching every other test in this suite.
 *
 * <p><strong>Why the WebP fixtures are hand-assembled.</strong> The JDK ships no
 * WebP encoder — {@code ImageIO.write(..., "webp", ...)} silently writes nothing
 * — and no binary fixtures are committed to this repository. The three builders
 * below therefore emit real RIFF/WebP headers byte by byte, one for each of the
 * three encodings a browser can produce (lossy {@code VP8 }, lossless
 * {@code VP8L}, extended {@code VP8X}). They are headers only: the service reads
 * dimensions from the header and never decodes pixels, which is exactly the
 * property that keeps a decompression bomb from reaching the CPU.
 */
class HighlightImageStorageTest {

    /** The production cap from {@code application.properties}. */
    private static final long TWO_MB = 2_097_152L;

    @TempDir
    Path tempRoot;

    /** The service's own directory. Deliberately a child of the temp root, so
     *  "escape the directory" has somewhere real to escape to. */
    private Path storageDir;

    /** Lives one level above {@link #storageDir}. Nothing the service does may
     *  read, overwrite or delete this file. */
    private Path sentinelOutside;

    private HighlightImageStorageServiceImpl storage;

    @BeforeEach
    void setUp() throws IOException {
        storageDir = tempRoot.resolve("highlights");
        sentinelOutside = tempRoot.resolve("sentinel.txt");
        Files.writeString(sentinelOutside, "must survive every test in this file");
        storage = new HighlightImageStorageServiceImpl(storageDir.toString(), TWO_MB);
    }

    // ------------------------------------------------------------------
    // Format detection — the bytes decide, not the name
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A PDF renamed .png is rejected: renaming a file does not change its format")
    void pdfRenamedAsPngIsRejected() throws IOException {
        MultipartFile disguised = new MockMultipartFile("file", "poster.png", "image/png",
                "%PDF-1.7\n1 0 obj<</Type/Catalog>>endobj".getBytes(StandardCharsets.US_ASCII));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> storage.store(disguised));

        assertTrue(ex.getMessage().contains("Only PNG, JPEG and WebP"), ex.getMessage());
        assertNothingWasStored();
    }

    @Test
    @DisplayName("An HTML page renamed .jpg is rejected — the stored-XSS vector this guard exists for")
    void htmlRenamedAsJpegIsRejected() throws IOException {
        MultipartFile disguised = new MockMultipartFile("file", "banner.jpg", "image/jpeg",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.US_ASCII));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> storage.store(disguised));

        assertTrue(ex.getMessage().contains("Only PNG, JPEG and WebP"), ex.getMessage());
        assertNothingWasStored();
    }

    @Test
    @DisplayName("A PNG whose header stops after the signature is rejected as unreadable")
    void truncatedPngIsRejected() throws IOException {
        // A valid 8-byte PNG signature and nothing else: detection passes, the
        // dimension read fails. Both halves of the check have to run.
        byte[] signatureOnly = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MultipartFile truncated = new MockMultipartFile("file", "cut.png", "image/png", signatureOnly);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> storage.store(truncated));

        assertTrue(ex.getMessage().contains("unreadable"), ex.getMessage());
        assertNothingWasStored();
    }

    @Test
    @DisplayName("A WebP truncated before its dimension bytes is rejected rather than stored at 0 x 0")
    void truncatedWebpIsRejected() throws IOException {
        // RIFF + WEBP present, so detection succeeds; too short to hold a
        // dimension field. image_width / image_height are NOT NULL, so a 0 x 0
        // read must be a rejection, never a silent write.
        byte[] header = new byte[20];
        writeAscii(header, 0, "RIFF");
        writeLe32(header, 4, header.length - 8);
        writeAscii(header, 8, "WEBP");
        writeAscii(header, 12, "VP8X");
        MultipartFile truncated = new MockMultipartFile("file", "cut.webp", "image/webp", header);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> storage.store(truncated));

        assertTrue(ex.getMessage().contains("unreadable"), ex.getMessage());
        assertNothingWasStored();
    }

    @Test
    @DisplayName("An empty upload, and a null upload, are both rejected")
    void emptyAndNullUploadsAreRejected() throws IOException {
        MultipartFile empty = new MockMultipartFile("file", "nothing.png", "image/png", new byte[0]);

        assertEquals("No image file was submitted.",
                assertThrows(BadRequestException.class, () -> storage.store(empty)).getMessage());
        assertEquals("No image file was submitted.",
                assertThrows(BadRequestException.class, () -> storage.store(null)).getMessage());
        assertNothingWasStored();
    }

    // ------------------------------------------------------------------
    // The happy paths — one per accepted format
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A real PNG is accepted and measured from its header")
    void pngIsAcceptedAndMeasured() throws IOException {
        MultipartFile png = new MockMultipartFile("file", "banner.png", "image/png", pngBytes(1600, 730));

        HighlightImageStorageService.StoredImage stored = storage.store(png);

        assertEquals("image/png", stored.contentType());
        assertEquals(1600, stored.width());
        assertEquals(730, stored.height());
        assertTrue(stored.filename().endsWith(".png"), stored.filename());
        assertStoredInsideDirectory(stored.filename());
    }

    @Test
    @DisplayName("A real JPEG is accepted and measured from its header")
    void jpegIsAcceptedAndMeasured() throws IOException {
        MultipartFile jpeg = new MockMultipartFile("file", "award.jpg", "image/jpeg", jpegBytes(800, 600));

        HighlightImageStorageService.StoredImage stored = storage.store(jpeg);

        assertEquals("image/jpeg", stored.contentType());
        assertEquals(800, stored.width());
        assertEquals(600, stored.height());
        assertStoredInsideDirectory(stored.filename());
    }

    @Test
    @DisplayName("Lossy WebP (VP8 ) is accepted — 534 x 467, the real size of the 5G lab poster")
    void lossyWebpIsAcceptedAndMeasured() throws IOException {
        MultipartFile webp = new MockMultipartFile("file", "lab.webp", "image/webp", webpVp8(534, 467));

        HighlightImageStorageService.StoredImage stored = storage.store(webp);

        assertEquals("image/webp", stored.contentType());
        assertEquals(534, stored.width());
        assertEquals(467, stored.height());
        assertStoredInsideDirectory(stored.filename());
    }

    @Test
    @DisplayName("Lossless WebP (VP8L) is accepted and measured from its packed 14-bit fields")
    void losslessWebpIsAcceptedAndMeasured() throws IOException {
        MultipartFile webp = new MockMultipartFile("file", "poster.webp", "image/webp", webpVp8l(800, 600));

        HighlightImageStorageService.StoredImage stored = storage.store(webp);

        assertEquals("image/webp", stored.contentType());
        assertEquals(800, stored.width());
        assertEquals(600, stored.height());
    }

    @Test
    @DisplayName("Extended WebP (VP8X) is accepted — the encoding an alpha channel forces")
    void extendedWebpIsAcceptedAndMeasured() throws IOException {
        MultipartFile webp = new MockMultipartFile("file", "poster.webp", "image/webp", webpVp8x(1600, 730));

        HighlightImageStorageService.StoredImage stored = storage.store(webp);

        assertEquals("image/webp", stored.contentType());
        assertEquals(1600, stored.width());
        assertEquals(730, stored.height());
    }

    @Test
    @DisplayName("The stored extension follows the detected format, not the submitted filename")
    void detectedFormatDecidesTheStoredExtension() throws IOException {
        // Real PNG bytes, but the browser insists it is a JPEG in both the
        // filename and the Content-Type header. Both claims are ignored.
        MultipartFile mislabelled =
                new MockMultipartFile("file", "poster.jpg", "image/jpeg", pngBytes(1600, 730));

        HighlightImageStorageService.StoredImage stored = storage.store(mislabelled);

        assertEquals("image/png", stored.contentType());
        assertTrue(stored.filename().endsWith(".png"), stored.filename());

        // And the reverse direction: real JPEG bytes named .png must not be
        // stored as .png, or Caddy would serve it under the wrong type.
        MultipartFile theOtherWayRound =
                new MockMultipartFile("file", "poster.png", "image/png", jpegBytes(800, 600));

        HighlightImageStorageService.StoredImage second = storage.store(theOtherWayRound);

        assertEquals("image/jpeg", second.contentType());
        assertFalse(second.filename().endsWith(".png"), second.filename());
    }

    // ------------------------------------------------------------------
    // Size and dimension caps
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A 3 MB upload is rejected against the 2 MB cap")
    void oversizedUploadIsRejected() throws IOException {
        byte[] threeMegabytes = new byte[3 * 1024 * 1024];
        // Give it a valid PNG signature so the rejection can only be the cap.
        System.arraycopy(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                0, threeMegabytes, 0, 8);
        MultipartFile huge = new MockMultipartFile("file", "huge.png", "image/png", threeMegabytes);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> storage.store(huge));

        assertTrue(ex.getMessage().contains("too large"), ex.getMessage());
        assertNothingWasStored();
    }

    @Test
    @DisplayName("A client that under-reports getSize() still cannot get past the cap")
    void aLyingContentLengthDoesNotDefeatTheCap() throws IOException {
        byte[] threeMegabytes = new byte[3 * 1024 * 1024];
        System.arraycopy(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                0, threeMegabytes, 0, 8);

        /* getSize() is metadata a client controls; the byte array is the payload.
           This is why the service checks both, and this test is the only thing
           that keeps the second check from looking like dead code and being
           "tidied away" later. */
        MultipartFile liar = new MockMultipartFile("file", "small.png", "image/png", threeMegabytes) {
            @Override
            public long getSize() {
                return 42L;
            }
        };

        BadRequestException ex = assertThrows(BadRequestException.class, () -> storage.store(liar));

        assertTrue(ex.getMessage().contains("too large"), ex.getMessage());
        assertNothingWasStored();
    }

    @Test
    @DisplayName("A header claiming 20 000 pixels wide is rejected before any pixel is decoded")
    void absurdDimensionsAreRejected() throws IOException {
        // 30 bytes on the wire, 20 000 x 500 in the header. Nothing decodes it,
        // which is the entire point: the guard costs a header read, not RAM.
        MultipartFile bomb = new MockMultipartFile("file", "bomb.webp", "image/webp", webpVp8x(20_000, 500));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> storage.store(bomb));

        assertTrue(ex.getMessage().contains("far larger"), ex.getMessage());
        assertNothingWasStored();
    }

    @Test
    @DisplayName("The cap is configurable, and a smaller one is enforced")
    void theCapComesFromConfiguration() throws IOException {
        HighlightImageStorageServiceImpl tightCap =
                new HighlightImageStorageServiceImpl(storageDir.toString(), 1024L);
        MultipartFile png = new MockMultipartFile("file", "banner.png", "image/png", pngBytes(1600, 730));

        assertThrows(BadRequestException.class, () -> tightCap.store(png));
        // The same file sails through the production cap.
        assertDoesNotThrow(() -> storage.store(png));
    }

    // ------------------------------------------------------------------
    // Path traversal — write, read and delete
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A ../ filename cannot escape the highlights directory; the name is replaced by a UUID")
    void submittedFilenameCannotEscapeTheDirectory() throws IOException {
        MultipartFile traversal = new MockMultipartFile(
                "file", "../../evil.png", "image/png", pngBytes(400, 300));

        HighlightImageStorageService.StoredImage stored = storage.store(traversal);

        assertFalse(stored.filename().contains(".."), stored.filename());
        assertFalse(stored.filename().contains("/"), stored.filename());
        assertFalse(stored.filename().contains("\\"), stored.filename());
        // The name is a UUID plus an extension, so the original string is gone
        // entirely rather than merely sanitised.
        String withoutExtension = stored.filename().substring(0, stored.filename().lastIndexOf('.'));
        assertDoesNotThrow(() -> UUID.fromString(withoutExtension), stored.filename());

        assertStoredInsideDirectory(stored.filename());
        assertFalse(Files.exists(tempRoot.resolve("evil.png")), "wrote one level up");
        assertTrue(Files.exists(sentinelOutside), "the file above the storage directory was disturbed");
    }

    @Test
    @DisplayName("loadAsResource refuses to read outside the highlights directory")
    void loadAsResourceRefusesTraversal() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> storage.loadAsResource("../sentinel.txt"));

        assertEquals("Path traversal attempt detected and blocked.", ex.getMessage());
    }

    @Test
    @DisplayName("loadAsResource 404s for a missing, blank or null filename — no existence disclosure")
    void loadAsResourceHidesWhatIsNotThere() {
        assertThrows(ResourceNotFoundException.class, () -> storage.loadAsResource("no-such-file.png"));
        assertThrows(ResourceNotFoundException.class, () -> storage.loadAsResource(""));
        assertThrows(ResourceNotFoundException.class, () -> storage.loadAsResource(null));
    }

    @Test
    @DisplayName("A stored image can be read back through loadAsResource")
    void aStoredImageCanBeReadBack() throws IOException {
        byte[] original = pngBytes(1600, 730);
        HighlightImageStorageService.StoredImage stored =
                storage.store(new MockMultipartFile("file", "banner.png", "image/png", original));

        Resource resource = storage.loadAsResource(stored.filename());

        assertTrue(resource.exists());
        assertEquals(original.length, resource.contentLength());
    }

    @Test
    @DisplayName("delete never throws, and never reaches outside the highlights directory")
    void deleteIsSafeAndScoped() throws IOException {
        // A missing file, a blank name and a null must all be no-ops: delete runs
        // after the transaction commits, where an exception has nowhere to go.
        assertDoesNotThrow(() -> storage.delete("never-existed.png"));
        assertDoesNotThrow(() -> storage.delete(""));
        assertDoesNotThrow(() -> storage.delete(null));

        assertDoesNotThrow(() -> storage.delete("../sentinel.txt"));
        assertTrue(Files.exists(sentinelOutside), "delete escaped the highlights directory");
    }

    @Test
    @DisplayName("delete removes the stored file")
    void deleteRemovesTheStoredFile() throws IOException {
        HighlightImageStorageService.StoredImage stored = storage.store(
                new MockMultipartFile("file", "banner.png", "image/png", pngBytes(400, 300)));
        assertTrue(Files.exists(storageDir.resolve(stored.filename())));

        storage.delete(stored.filename());

        assertFalse(Files.exists(storageDir.resolve(stored.filename())));
    }

    // ------------------------------------------------------------------
    // Assertions shared by the tests above
    // ------------------------------------------------------------------

    private void assertNothingWasStored() throws IOException {
        try (Stream<Path> entries = Files.list(storageDir)) {
            assertEquals(0, entries.count(), "a rejected upload still left a file on disk");
        }
        assertTrue(Files.exists(sentinelOutside), "the file above the storage directory was disturbed");
    }

    private void assertStoredInsideDirectory(String filename) {
        Path written = storageDir.resolve(filename);
        assertTrue(Files.exists(written), "not written to the highlights directory: " + filename);
        assertTrue(written.toAbsolutePath().normalize()
                        .startsWith(storageDir.toAbsolutePath().normalize()),
                "written outside the highlights directory: " + written);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** A real PNG of the requested size. ImageIO writes the header we then read back. */
    private static byte[] pngBytes(int width, int height) throws IOException {
        return encode(width, height, "png");
    }

    /** A real JPEG of the requested size. */
    private static byte[] jpegBytes(int width, int height) throws IOException {
        return encode(width, height, "jpg");
    }

    private static byte[] encode(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out), "no ImageIO writer for " + format);
        return out.toByteArray();
    }

    /**
     * A lossy WebP header: {@code RIFF....WEBPVP8 } followed by the 3-byte start
     * code and two 14-bit dimension fields. Dimensions are stored as-is (not
     * minus one) in this encoding.
     */
    private static byte[] webpVp8(int width, int height) {
        byte[] b = riffContainer("VP8 ");
        b[23] = (byte) 0x9D;   // key-frame start code, checked by the parser
        b[24] = 0x01;
        b[25] = 0x2A;
        writeLe16(b, 26, width);
        writeLe16(b, 28, height);
        return b;
    }

    /**
     * A lossless WebP header: signature byte {@code 0x2F}, then width-1 and
     * height-1 packed into 14 bits each inside one little-endian 32-bit word.
     */
    private static byte[] webpVp8l(int width, int height) {
        byte[] b = riffContainer("VP8L");
        b[20] = 0x2F;
        int packed = ((width - 1) & 0x3FFF) | (((height - 1) & 0x3FFF) << 14);
        writeLe32(b, 21, packed);
        return b;
    }

    /**
     * An extended WebP header: two 24-bit little-endian fields holding width-1
     * and height-1. This is what an encoder emits for an image with alpha or
     * animation, so it is the encoding a downloaded poster is most likely to be.
     */
    private static byte[] webpVp8x(int width, int height) {
        byte[] b = riffContainer("VP8X");
        writeLe24(b, 24, width - 1);
        writeLe24(b, 27, height - 1);
        return b;
    }

    /** 30 bytes: the shortest buffer the dimension parser will look at. */
    private static byte[] riffContainer(String fourCc) {
        byte[] b = new byte[30];
        writeAscii(b, 0, "RIFF");
        writeLe32(b, 4, b.length - 8);
        writeAscii(b, 8, "WEBP");
        writeAscii(b, 12, fourCc);
        writeLe32(b, 16, 10);   // chunk payload length
        return b;
    }

    private static void writeAscii(byte[] target, int offset, String value) {
        byte[] raw = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(raw, 0, target, offset, raw.length);
    }

    private static void writeLe16(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void writeLe24(byte[] target, int offset, int value) {
        writeLe16(target, offset, value);
        target[offset + 2] = (byte) ((value >>> 16) & 0xFF);
    }

    private static void writeLe32(byte[] target, int offset, int value) {
        writeLe24(target, offset, value);
        target[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }
}
