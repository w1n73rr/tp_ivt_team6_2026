package tech.genna.TY3.tests.ui.support;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Small valid documents with the same ten source facts, not renamed extensions. */
public final class GenerationFiles implements AutoCloseable {
    private final Path dir = Files.createTempDirectory("generation-formats-");
    private static final List<String> FACTS = List.of(
            "Unit testing checks individual components in isolation.", "Integration testing checks interactions between components.",
            "System testing checks the complete system against requirements.", "Acceptance testing checks customer needs.",
            "Regression testing checks previously working behavior after changes.", "Positive tests use valid input data.",
            "Negative tests check invalid data and failure conditions.", "Boundary values lie at or near allowed range boundaries.",
            "A test case contains preconditions, inputs, steps and expected results.",
            "A mock controls a dependency and does not prove real integration.");
    public GenerationFiles() throws IOException {}
    public Path file(String format) throws IOException {
        Path path = dir.resolve("lecture." + format.toLowerCase(Locale.ROOT));
        if (format.equals("TXT")) Files.writeString(path, String.join("\n", FACTS), StandardCharsets.UTF_8);
        else if (format.equals("DOCX")) {
            try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
                entry(zip, "[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
                entry(zip, "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"r1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
                entry(zip, "word/document.xml", "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + String.join("", FACTS.stream().map(f -> "<w:p><w:r><w:t>" + f + "</w:t></w:r></w:p>").toList()) + "</w:body></w:document>");
            }
        } else if (format.equals("PDF")) {
            String stream = "BT /F1 10 Tf 40 740 Td 18 TL\n" + String.join("\n", FACTS.stream().map(f -> "(" + f + ") Tj T*").toList()) + "\nET\n";
            var objects = List.of("<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>", "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>", "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>", "<< /Length " + stream.length() + " >>\nstream\n" + stream + "endstream");
            var bytes = new ByteArrayOutputStream();
            bytes.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
            var offsets = new ArrayList<Integer>();
            for (int i = 0; i < objects.size(); i++) { offsets.add(bytes.size()); bytes.write(((i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n").getBytes(StandardCharsets.US_ASCII)); }
            int xref = bytes.size();
            bytes.write("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
            for (int offset : offsets) bytes.write(String.format(Locale.ROOT, "%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
            bytes.write(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
            Files.write(path, bytes.toByteArray());
        } else throw new IllegalArgumentException("Unknown fixture format");
        return path;
    }
    private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    @Override public void close() throws IOException {
        try (var files = Files.list(dir)) { for (var file : files.toList()) Files.delete(file); }
        Files.delete(dir);
    }
}
