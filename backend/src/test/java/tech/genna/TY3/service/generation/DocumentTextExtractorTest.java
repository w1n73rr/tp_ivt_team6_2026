package tech.genna.TY3.service.generation;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentTextExtractorTest {
    private final DocumentTextExtractor extractor = new DocumentTextExtractor();
    private String extract(byte[] bytes, String format) { return extractor.extract(new ByteArrayInputStream(bytes), format); }
    private void fails(String code, byte[] bytes, String format) {
        assertEquals(code, assertThrows(GenerationFailure.class, () -> extract(bytes, format)).code());
    }
    static byte[] pdf(boolean text, boolean protectedFile) throws Exception {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            var page = new PDPage(); document.addPage(page);
            if (text) try (var content = new PDPageContentStream(document, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700); content.showText("Learning document"); content.endText();
            }
            if (protectedFile) document.protect(new StandardProtectionPolicy("owner-secret", "reader-secret", new AccessPermission()));
            document.save(bytes); return bytes.toByteArray();
        }
    }
    static byte[] docx(String body) throws IOException {
        try (var bytes = new ByteArrayOutputStream()) {
            try (var zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zip.write("<Types/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                zip.write(body.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            return bytes.toByteArray();
        }
    }
    @Test void extractsRealPdfDocxAndUtf8TxtBytes() throws Exception {
        assertEquals("Learning document", extract(pdf(true, false), "PDF"));
        assertEquals("Учебный текст", extract(docx("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Учебный текст</w:t></w:r></w:p></w:body></w:document>"), "DOCX"));
        assertEquals("Учебный текст", extract("\uFEFFУчебный текст\n".getBytes(StandardCharsets.UTF_8), "TXT"));
    }
    @Test void rejectsScanProtectedPdfAndUnreadableDocuments() throws Exception {
        fails("DOCUMENT_NO_TEXT", pdf(false, false), "PDF");
        fails("DOCUMENT_PROTECTED", pdf(true, true), "PDF");
        for (var format : new String[]{"PDF", "DOCX"}) fails("DOCUMENT_UNREADABLE", new byte[]{1, 2}, format);
        fails("DOCUMENT_UNREADABLE", new byte[]{(byte) 0xff}, "TXT");
        fails("DOCUMENT_NO_TEXT", " \n\t".getBytes(StandardCharsets.UTF_8), "TXT");
        fails("DOCUMENT_TEXT_TOO_LARGE", "a".repeat(30001).getBytes(StandardCharsets.UTF_8), "TXT");
        fails("DOCUMENT_TOO_LARGE", new byte[DocumentTextExtractor.MAX_FILE_BYTES + 1], "TXT");
    }
    @Test void rejectsDocxWithoutTextAndExternalEntities() throws Exception {
        fails("DOCUMENT_NO_TEXT", docx("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>"), "DOCX");
        fails("DOCUMENT_UNREADABLE", docx("<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///should-never-be-read'>]><x>&x;</x>"), "DOCX");
        fails("DOCUMENT_PROTECTED", new byte[]{(byte)0xd0, (byte)0xcf, 0x11, (byte)0xe0}, "DOCX");
    }
}
