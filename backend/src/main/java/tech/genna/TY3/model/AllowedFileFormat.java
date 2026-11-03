package tech.genna.TY3.model;

import tech.genna.TY3.api.model.FileFormat;

import java.util.Arrays;
import java.util.Optional;

/** Single backend registry of formats accepted for material uploads. */
public enum AllowedFileFormat {
    PDF("pdf", "application/pdf", FileFormat.PDF),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", FileFormat.DOCX),
    TXT("txt", "text/plain; charset=UTF-8", FileFormat.TXT);

    private final String extension;
    private final String contentType;
    private final FileFormat apiFormat;

    AllowedFileFormat(String extension, String contentType, FileFormat apiFormat) {
        this.extension = extension;
        this.contentType = contentType;
        this.apiFormat = apiFormat;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public FileFormat apiFormat() {
        return apiFormat;
    }

    public static Optional<AllowedFileFormat> fromExtension(String extension) {
        return Arrays.stream(values())
                .filter(format -> format.extension.equalsIgnoreCase(extension))
                .findFirst();
    }
}
