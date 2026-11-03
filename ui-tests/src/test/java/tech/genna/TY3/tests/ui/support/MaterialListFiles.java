package tech.genna.TY3.tests.ui.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class MaterialListFiles implements AutoCloseable {
    private final Path directory;
    public final Path pdf;
    public final Path docx;
    public final Path txt;

    public MaterialListFiles() throws IOException {
        directory = Files.createTempDirectory("material-list-ui-");
        pdf = directory.resolve("algebra.pdf");
        docx = directory.resolve("databases.docx");
        txt = directory.resolve("sql.txt");
        writePdf();
        writeDocx();
        Files.writeString(txt, "SQL\n".repeat(262_144), StandardCharsets.UTF_8);
    }

    private void writePdf() throws IOException {
        ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
        pdfBytes.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        String content = "BT /F1 12 Tf 72 720 Td (Algebra lecture) Tj ET\n";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "endstream");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdfBytes.size());
            pdfBytes.write(((i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n").getBytes(StandardCharsets.US_ASCII));
        }
        int xref = pdfBytes.size();
        pdfBytes.write("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int offset : offsets) {
            pdfBytes.write(String.format(Locale.ROOT, "%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
        }
        pdfBytes.write(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        Files.write(pdf, pdfBytes.toByteArray());
    }

    private void writeDocx() throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(docx))) {
            zip.setLevel(0);
            entry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            entry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            entry(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                    """ + "<w:p><w:r><w:t>Database lecture: SQL and relations.</w:t></w:r></w:p>".repeat(50)
                    + "<w:sectPr/></w:body></w:document>");
        }
    }

    private void entry(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(pdf);
        Files.deleteIfExists(docx);
        Files.deleteIfExists(txt);
        Files.deleteIfExists(directory);
    }
}
