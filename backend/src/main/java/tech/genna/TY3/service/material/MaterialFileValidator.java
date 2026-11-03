package tech.genna.TY3.service.material;

import tech.genna.TY3.model.AllowedFileFormat;

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
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);
        AllowedFileFormat format = AllowedFileFormat.fromExtension(extension)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE"));
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
        // Exhaustive switch: adding a registry entry also requires a content validator.
        byte[] validatedBytes = switch (format) {
            case TXT -> {
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
                yield bytes;
            }
            case PDF -> {
                String header = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
                String tail = new String(bytes, Math.max(0, bytes.length - 2048),
                        Math.min(bytes.length, 2048), StandardCharsets.US_ASCII);
                if (!header.matches("%PDF-[12]\\.[0-9]") || !tail.contains("%%EOF")) throw badFile();
                yield bytes;
            }
            case DOCX -> {
                validateDocx(bytes);
                yield bytes;
            }
        };
        return new ValidatedFile(validatedBytes, name, format.apiFormat().getValue(), format.contentType());
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
