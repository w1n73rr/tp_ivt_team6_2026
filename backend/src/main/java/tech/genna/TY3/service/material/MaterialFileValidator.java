package tech.genna.TY3.service.material;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipInputStream;

@Component
public class MaterialFileValidator {
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw badFile();
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        String name = file.getOriginalFilename();
        if (name == null) throw badFile();
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw badFile();
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            throw badFile();
        }
        if (bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        if (bytes.length == 0) throw badFile();
        String contentType = switch (extension) {
            case "txt" -> {
                try {
                    String text = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString();
                    if (text.replace("\uFEFF", "").isBlank() || text.codePoints()
                            .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
                        throw badFile();
                    }
                } catch (CharacterCodingException e) {
                    throw badFile();
                }
                yield "text/plain; charset=UTF-8";
            }
            case "pdf" -> {
                String header = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
                String tail = new String(bytes, Math.max(0, bytes.length - 2048),
                        Math.min(bytes.length, 2048), StandardCharsets.US_ASCII);
                if (!header.matches("%PDF-[12]\\.[0-9]") || !tail.contains("%%EOF")) throw badFile();
                yield "application/pdf";
            }
            case "docx" -> {
                validateDocx(bytes);
                yield "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
        };
        return new ValidatedFile(bytes, name, extension.toUpperCase(Locale.ROOT), contentType);
    }

    private void validateDocx(byte[] bytes) {
        boolean contentTypes = false;
        boolean document = false;
        long expandedBytes = 0;
        int entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            var buffer = new byte[8192];
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 2000) throw badFile();
                long entryBytes = 0;
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expandedBytes += count;
                    entryBytes += count;
                    if (expandedBytes > 50L * 1024 * 1024) throw badFile();
                }
                contentTypes |= entry.getName().equals("[Content_Types].xml") && entryBytes > 0;
                document |= entry.getName().equals("word/document.xml") && entryBytes > 0;
            }
        } catch (IOException e) {
            throw badFile();
        }
        if (!contentTypes || !document) throw badFile();
    }

    private ResponseStatusException badFile() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT");
    }

    public record ValidatedFile(byte[] bytes, String filename, String format, String contentType) {}
}
