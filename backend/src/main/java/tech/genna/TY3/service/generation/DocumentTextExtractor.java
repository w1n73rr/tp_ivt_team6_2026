package tech.genna.TY3.service.generation;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.HashSet;
import java.util.zip.ZipInputStream;

@Component
public class DocumentTextExtractor {
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    public static final int MAX_TEXT_POINTS = 30_000;
    private static final int MAX_XML_BYTES = 20 * 1024 * 1024;

    public String extract(InputStream input, String format) {
        try {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new GenerationFailure("DOCUMENT_TOO_LARGE");
            String text = switch (format) {
                case "PDF" -> pdf(bytes);
                case "DOCX" -> docx(bytes);
                case "TXT" -> StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString().replace("\uFEFF", "");
                default -> throw new GenerationFailure("DOCUMENT_UNREADABLE");
            };
            if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && !Character.isWhitespace(c)))
                throw new GenerationFailure("DOCUMENT_UNREADABLE");
            text = text.strip();
            if (text.isBlank() || text.codePoints().noneMatch(Character::isLetterOrDigit))
                throw new GenerationFailure("DOCUMENT_NO_TEXT");
            if (text.codePointCount(0, text.length()) > MAX_TEXT_POINTS)
                throw new GenerationFailure("DOCUMENT_TEXT_TOO_LARGE");
            return text;
        } catch (GenerationFailure e) {
            throw e;
        } catch (InvalidPasswordException e) {
            throw new GenerationFailure("DOCUMENT_PROTECTED");
        } catch (Exception e) {
            throw new GenerationFailure("DOCUMENT_UNREADABLE");
        }
    }

    private String pdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent())
                throw new GenerationFailure("DOCUMENT_PROTECTED");
            if (document.getNumberOfPages() > 10) throw new GenerationFailure("DOCUMENT_TOO_MANY_PAGES");
            return new PDFTextStripper().getText(document);
        }
    }

    private String docx(byte[] bytes) throws Exception {
        // Encrypted Office documents are OLE containers rather than OPC ZIP files.
        if (bytes.length >= 4 && bytes[0] == (byte) 0xD0 && bytes[1] == (byte) 0xCF
                && bytes[2] == (byte) 0x11 && bytes[3] == (byte) 0xE0)
            throw new GenerationFailure("DOCUMENT_PROTECTED");
        byte[] xml = null;
        byte[] settings = null;
        boolean contentTypes = false;
        long expanded = 0;
        var names = new HashSet<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!names.add(entry.getName()) || names.size() > 2048)
                    throw new GenerationFailure("DOCUMENT_UNREADABLE");
                byte[] data = zip.readNBytes(MAX_XML_BYTES + 1);
                expanded += data.length;
                if (expanded > MAX_XML_BYTES) throw new GenerationFailure("DOCUMENT_TOO_LARGE");
                if ("[Content_Types].xml".equals(entry.getName())) contentTypes = true;
                if ("word/document.xml".equals(entry.getName())) xml = data;
                if ("word/settings.xml".equals(entry.getName())) settings = data;
            }
        }
        if (!contentTypes || xml == null) throw new GenerationFailure("DOCUMENT_UNREADABLE");
        var factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var text = new StringBuilder();
        var handler = new DefaultHandler() {
            private boolean inText;
            private boolean word(String uri) {
                return "http://schemas.openxmlformats.org/wordprocessingml/2006/main".equals(uri)
                        || "http://purl.oclc.org/ooxml/wordprocessingml/main".equals(uri);
            }
            @Override public void startElement(String uri, String local, String q, Attributes a) {
                if (word(uri) && "documentProtection".equals(local))
                    throw new GenerationFailure("DOCUMENT_PROTECTED");
                inText = word(uri) && "t".equals(local);
                if (word(uri)) {
                    switch (local) {
                        case "tab" -> text.append('\t');
                        case "br", "cr" -> text.append('\n');
                        default -> { }
                    }
                    if (text.length() > MAX_TEXT_POINTS * 2)
                        throw new GenerationFailure("DOCUMENT_TEXT_TOO_LARGE");
                }
            }
            @Override public void characters(char[] ch, int start, int length) {
                if (inText) {
                    text.append(ch, start, length);
                    if (text.length() > MAX_TEXT_POINTS * 2) throw new GenerationFailure("DOCUMENT_TEXT_TOO_LARGE");
                }
            }
            @Override public void endElement(String uri, String local, String q) {
                inText = false;
                if (word(uri) && ("p".equals(local) || "tc".equals(local))) text.append('\n');
            }
        };
        if (settings != null) factory.newSAXParser().parse(new ByteArrayInputStream(settings), handler);
        factory.newSAXParser().parse(new ByteArrayInputStream(xml), handler);
        return text.toString();
    }
}
