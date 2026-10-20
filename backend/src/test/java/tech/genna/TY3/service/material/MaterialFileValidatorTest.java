package tech.genna.TY3.service.material;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MaterialFileValidatorTest {
    private final MaterialFileValidator validator = new MaterialFileValidator();

    @Test
    void acceptsPdfAndDerivesMimeFromContentInsteadOfClientHeader() {
        var result = validator.validate(file("lecture.PDF", "%PDF-1.7\n%%EOF"));
        assertEquals("PDF", result.format());
        assertEquals("application/pdf", result.contentType());
    }

    @Test
    void acceptsUtf8Text() {
        var result = validator.validate(file("lecture.txt", "Текст лекции"));
        assertEquals("TXT", result.format());
        assertEquals("text/plain; charset=UTF-8", result.contentType());
    }

    @Test
    void acceptsDocx() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String entry : new String[]{"[Content_Types].xml", "word/document.xml"}) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write("<xml/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        var result = validator.validate(new MockMultipartFile(
                "file", "lecture.docx", "application/octet-stream", bytes.toByteArray()));
        assertEquals("DOCX", result.format());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                result.contentType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lecture.exe", "lecture.doc", "lecture.zip", "lecture.pdf.exe", "pdf", "lecture."})
    void rejectsUnsupportedExtensionsEvenWithPdfMime(String filename) {
        var file = new MockMultipartFile("file", filename, "application/pdf",
                "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.UTF_8));
        var error = assertThrows(ResponseStatusException.class, () -> validator.validate(file));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatusCode());
        assertEquals("UNSUPPORTED_FILE_TYPE", error.getReason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lecture.pdf", "lecture.docx"})
    void rejectsContentDisguisedAsSupportedFormat(String filename) {
        var error = assertThrows(ResponseStatusException.class,
                () -> validator.validate(file(filename, "Not a document of this format")));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsNonUtf8Text() {
        var file = new MockMultipartFile("file", "lecture.txt", "text/plain", new byte[]{(byte) 0xff});
        assertThrows(ResponseStatusException.class, () -> validator.validate(file));
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
