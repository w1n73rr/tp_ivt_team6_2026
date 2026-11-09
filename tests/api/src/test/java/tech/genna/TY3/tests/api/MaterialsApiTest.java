package tech.genna.TY3.tests.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Black-box HTTP checks. No Spring context, database access, UI, or storage mocks. */
class MaterialsApiTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> AUDIT = new ArrayList<>();
    private static String base;
    private String a, b, m1, m2, m3, label;
    private final List<Map.Entry<String, String>> owned = new ArrayList<>();
    private static final byte[] TXT = "Lecture: algebra, geometry and SQL.\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jN1kAAAAASUVORK5CYII=");

    @BeforeAll static void requireDisposableEnvironment() {
        assertEquals("true", System.getenv("API_TEST_DISPOSABLE"),
                "Run only on the disposable API test stack; API_TEST_DISPOSABLE=true is required");
        base = Objects.requireNonNull(System.getenv("API_BASE_URL"), "API_BASE_URL is required");
        assertTrue(base.startsWith("http://"), "Use the private disposable HTTP stack");
    }

    @BeforeEach void prepare(TestInfo info) throws Exception {
        label = info.getDisplayName();
        a = register(); b = register();
        m1 = create(a, "Алгебра", "Математика", true, "lecture.txt", TXT).path("material.id");
        m2 = create(a, "Геометрия", "Математика", false, "lecture.txt", TXT).path("material.id");
        m3 = create(b, "Физика", "Физика", false, "lecture.txt", TXT).path("material.id");
    }

    @AfterEach void cleanCreatedMaterials() {
        List<String> failures = new ArrayList<>();
        for (var item : owned) {
            try {
                Response r = request(item.getKey()).delete("/api/materials/" + item.getValue());
                record("DELETE", "/api/materials/" + item.getValue(), "fixture cleanup", "204 or 404", r.statusCode());
                if (r.statusCode() != 204 && r.statusCode() != 404) failures.add(item.getValue() + ": " + r.statusCode());
            } catch (Exception e) { failures.add(item.getValue() + ": connection failure"); }
        }
        assertTrue(failures.isEmpty(), "Fixture cleanup failed: " + failures);
    }

    @AfterAll static void writeHttpAudit() throws Exception {
        Path report = Path.of("build/reports/materials-http.md");
        Files.createDirectories(report.getParent());
        String header = "# HTTP audit — materials API\n\nVersion: "
                + System.getenv().getOrDefault("API_TEST_VERSION", "unspecified")
                + "\n\nEnvironment: disposable Docker stack; real BE/PostgreSQL/SeaweedFS, no mocks.\n"
                + "HTTP codes alone do not confirm field assertions: see the JUnit HTML/XML report.\n"
                + "Passwords, tokens, response bodies and authorization headers are excluded.\n\n"
                + "| Test | Method | Endpoint | Input | Expected HTTP | Actual HTTP |\n"
                + "|---|---|---|---|---|---|\n";
        Files.writeString(report, header + String.join("\n", AUDIT) + "\n", StandardCharsets.UTF_8);
    }

    private String register() throws Exception {
        String name = "qa41_" + UUID.randomUUID().toString().replace("-", "");
        Response r = check("POST", "/api/auth/register", "unique test account; password omitted", 200,
                request(null).contentType("application/json").body(JSON.writeValueAsString(Map.of(
                        "username", name, "email", name + "@example.com", "password", UUID.randomUUID() + "Qa!"))));
        String token = r.path("token");
        assertNotNull(token, "Registration must return a token");
        return token;
    }

    private RequestSpecification request(String token) {
        var req = RestAssured.given().baseUri(base).redirects().follow(false)
                .config(RestAssured.config().httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 30000).setParam("http.socket.timeout", 60000)));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return req;
    }
    private Response check(String method, String path, String input, int expected, RequestSpecification req) {
        Response r;
        try { r = req.request(method, path); }
        catch (Exception e) {
            // Record only exception types: messages/causes may contain request credentials.
            Throwable root = e;
            for (int depth = 0; depth < 20 && root.getCause() != null && root.getCause() != root; depth++) {
                root = root.getCause();
            }
            String failure = "request failed before HTTP response: " + e.getClass().getSimpleName()
                    + "; root=" + root.getClass().getSimpleName();
            record(method, path, input, Integer.toString(expected), failure);
            throw new AssertionError(method + " " + path + ": " + failure);
        }
        record(method, path, input, Integer.toString(expected), r.statusCode());
        assertEquals(expected, r.statusCode(), method + " " + path + " / " + input);
        return r;
    }
    private void record(String method, String path, String input, String expected, Object actual) {
        AUDIT.add("| " + label.replace('|', '/') + " | " + method + " | " + path + " | "
                + input.replace('|', '/').replace('\n', ' ') + " | " + expected + " | " + actual + " |");
    }
    private Map<String, Object> metadata(String name, String subject, boolean optional) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name); data.put("subjectName", subject);
        if (optional) { data.put("description", "Материал для проверки API"); data.put("newTagNames", List.of("SQL")); }
        return data;
    }
    private RequestSpecification upload(String token, Object metadata, String filename, byte[] bytes) throws Exception {
        var req = request(token);
        if (metadata != null) {
            String json = metadata instanceof String s ? s : JSON.writeValueAsString(metadata);
            // A string multipart part may use a legacy charset and replace Cyrillic with '?'.
            req.multiPart("metadata", "metadata.json", json.getBytes(StandardCharsets.UTF_8),
                    "application/json");
        }
        if (bytes != null) req.multiPart("file", filename, bytes,
                filename.endsWith(".txt") ? "text/plain" : filename.endsWith(".pdf") ? "application/pdf" :
                        filename.endsWith(".png") ? "image/png" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return req;
    }
    private Response create(String token, String name, String subject, boolean optional, String filename, byte[] bytes) throws Exception {
        Response r = check("POST", "/api/materials", "file=" + filename + "; bytes=" + bytes.length
                        + "; name=" + name + "; subjectName=" + subject + "; optional=" + optional, 201,
                upload(token, metadata(name, subject, optional), filename, bytes));
        String id = r.path("material.id"); assertNotNull(id);
        owned.add(Map.entry(token, id));
        assertEquals("/api/materials/" + id, r.header("Location"));
        assertEquals(name, r.path("material.name"), "Uploaded name must retain its Unicode characters");
        assertEquals(subject, r.path("material.subjectName"), "Uploaded subject must retain its Unicode characters");
        return r;
    }
    private Response get(String token, String id, int status) {
        return check("GET", "/api/materials/" + id, "none", status, request(token));
    }
    private List<Map<String, Object>> list(String token) {
        return check("GET", "/api/materials", "none", 200, request(token)).path("items");
    }
    private Set<String> ids(String token) {
        Set<String> ids = new HashSet<>();
        for (var row : list(token)) assertTrue(ids.add((String) row.get("id")), "Duplicate material in list");
        return ids;
    }
    private Response patch(String token, String id, Object body, int status) throws Exception {
        String text = body instanceof String s ? s : JSON.writeValueAsString(body);
        return check("PATCH", "/api/materials/" + id, text, status,
                request(token).contentType("application/json; charset=UTF-8").body(text.getBytes(StandardCharsets.UTF_8)));
    }
    private Map<String, Object> detail(String token, String id) { return get(token, id, 200).path("$"); }

    private void assertStoredDetail(Map<String, Object> created, Map<String, Object> stored) {
        var expected = new LinkedHashMap<>(created);
        var actual = new LinkedHashMap<>(stored);
        for (String section : List.of("material", "processing")) {
            var before = new LinkedHashMap<>((Map<String, Object>) created.get(section));
            var after = new LinkedHashMap<>((Map<String, Object>) stored.get(section));
            String field = section.equals("material") ? "uploadedAt" : "updatedAt";
            var originalTime = OffsetDateTime.parse((String) before.remove(field)).toInstant();
            var storedTime = OffsetDateTime.parse((String) after.remove(field)).toInstant();
            // POST serializes Java nanoseconds; a subsequent GET reads DB microseconds.
            assertTrue(Duration.between(originalTime, storedTime).abs().toNanos() <= 1_000,
                    section + "." + field + " changed by more than one microsecond: " + originalTime + " -> " + storedTime);
            expected.put(section, before);
            actual.put(section, after);
        }
        assertEquals(expected, actual, "All other saved detail fields must match exactly");
    }

    @ParameterizedTest(name="TC-API-MAT-01: create {0}, optional={1}")
    @CsvSource({"pdf,true", "docx,true", "txt,true", "pdf,false", "docx,false", "txt,false"})
    void createFormats(String extension, boolean optional) throws Exception {
        byte[] bytes = fixture(extension);
        Response created = create(a, "Новый материал", "Базы данных", optional, "lecture." + extension, bytes);
        String id = created.path("material.id");
        Map<String, Object> expected = created.path("$");
        assertStoredDetail(expected, detail(a, id));
        assertEquals("Новый материал", created.path("material.name"));
        assertEquals("Базы данных", created.path("material.subjectName"));
        assertEquals(extension.toUpperCase(Locale.ROOT), created.path("material.format"));
        assertEquals(bytes.length, ((Number) created.path("material.sizeBytes")).intValue());
        assertEquals("lecture." + extension, created.path("originalFilename"));
        assertEquals(optional ? List.of("SQL") : List.of(), created.path("tags.name"));
        if (optional) assertEquals("Материал для проверки API", created.path("description"));
        assertEquals(Set.of(m1, m2, id), ids(a));
        byte[] downloaded = check("GET", "/api/materials/" + id + "/download", "none", 200, request(a)).asByteArray();
        assertArrayEquals(bytes, downloaded, "Original file must survive the storage round trip");
    }

    @Test @DisplayName("TC-API-MAT-02: own list and empty list")
    void ownList() throws Exception {
        assertEquals(Set.of(m1, m2), ids(a)); assertEquals(Set.of(m3), ids(b));
        for (var row : list(a)) assertEquals(detail(a, (String)row.get("id")).get("material"), row);
        assertTrue(list(register()).isEmpty());
    }
    @Test @DisplayName("TC-API-MAT-03: detail contains original metadata and processing")
    void ownDetail() {
        Response r = get(a, m1, 200);
        assertEquals(m1, r.path("material.id")); assertEquals("Алгебра", r.path("material.name"));
        assertEquals("Математика", r.path("material.subjectName")); assertEquals("TXT", r.path("material.format"));
        assertEquals(TXT.length, ((Number)r.path("material.sizeBytes")).intValue());
        assertNotNull(r.path("material.uploadedAt")); assertEquals("lecture.txt", r.path("originalFilename"));
        assertEquals(List.of("SQL"), r.path("tags.name"));
        assertEquals("Материал для проверки API", r.path("description"));
        assertEquals(m1, r.path("processing.materialId")); assertNotNull(r.path("processing.status"));
    }
    @Test @DisplayName("TC-API-MAT-04: change only name and subjectName")
    void updateAllowedFields() throws Exception {
        Map<String,Object> before = detail(a,m1), other = detail(a,m2);
        patch(a,m1,Map.of("name","Алгебра — итоговая версия","subjectName","Математика"),200);
        Response r = get(a,m1,200); assertEquals("Алгебра — итоговая версия",r.path("material.name"));
        assertEquals(List.of("SQL"), r.path("tags.name"));
        patch(a,m1,Map.of("name","Алгебра — итоговая версия","subjectName","Базы данных"),200);
        r = get(a,m1,200); assertEquals("Базы данных",r.path("material.subjectName"));
        assertTrue(((List<?>)r.path("tags")).isEmpty());
        Map<String,Object> original = (Map<String,Object>) before.get("material");
        for(String field:List.of("id","format","sizeBytes","uploadedAt")) assertEquals(original.get(field),r.path("material."+field));
        assertEquals(before.get("description"),r.path("description"));
        assertEquals(before.get("originalFilename"),r.path("originalFilename"));
        assertEquals(other,detail(a,m2));
    }
    @Test @DisplayName("TC-API-MAT-05: delete and repeated delete")
    void deleteMaterial() {
        Map<String,Object> other=detail(a,m2);
        Response r=check("DELETE","/api/materials/"+m1,"none",204,request(a));
        assertEquals(0,r.asByteArray().length); get(a,m1,404);
        assertEquals(Set.of(m2),ids(a)); assertEquals(other,detail(a,m2));
        check("DELETE","/api/materials/"+m1,"repeat deletion",404,request(a));
    }
    static Stream<Arguments> unauthorizedVariants() {
        return Stream.of("missing","invalid").flatMap(auth -> Stream.of("POST","LIST","GET","PATCH","DELETE").map(op -> Arguments.of(auth,op)));
    }
    @ParameterizedTest(name="TC-API-MAT-06: {0} authorization, {1}") @MethodSource("unauthorizedVariants")
    void unauthorized(String auth,String operation) throws Exception {
        String token=auth.equals("missing")?null:"not-a-valid-jwt";
        Map<String,Object> before=detail(a,m1);
        if(operation.equals("POST")) check("POST","/api/materials","valid multipart; "+auth+" auth",401,
                upload(token,metadata("Unauthorized upload","Математика",false),"lecture.txt",TXT));
        else if(operation.equals("PATCH")) patch(token,m1,Map.of("name","Changed","subjectName","Математика"),401);
        else check(operation.equals("LIST")?"GET":operation,operation.equals("LIST")?"/api/materials":"/api/materials/"+m1,auth+" auth",401,request(token));
        assertEquals(before,detail(a,m1)); assertEquals(Set.of(m1,m2),ids(a));
    }
    @ParameterizedTest(name="TC-API-MAT-07/08: {0} material") @ValueSource(strings={"missing","foreign"})
    void unavailable(String kind) throws Exception {
        String id=kind.equals("missing")?UUID.randomUUID().toString():m3;
        Map<String,Object> foreign=detail(b,m3), own=detail(a,m1);
        get(a,id,404); patch(a,id,Map.of("name","Changed","subjectName","Математика"),404);
        check("DELETE","/api/materials/"+id,kind,404,request(a));
        assertEquals(foreign,detail(b,m3)); assertEquals(own,detail(a,m1)); assertEquals(Set.of(m1,m2),ids(a));
    }

    static Stream<Arguments> invalidMetadata() {
        List<Arguments> args=new ArrayList<>();
        for(String method:List.of("POST","PATCH")) {
            for(String field:List.of("name","subjectName")) for(String variant:List.of("missing","empty","blank","long"))
                args.add(Arguments.of(method,field,variant));
            args.add(Arguments.of(method,"json","malformed"));
        }
        args.add(Arguments.of("POST","description","long"));
        for(String field:List.of("format","sizeBytes","uploadedAt")) args.add(Arguments.of("PATCH",field,"forbidden"));
        return args.stream();
    }
    @ParameterizedTest(name="TC-API-MAT-09: {0} {1} {2}") @MethodSource("invalidMetadata")
    void invalidMetadata(String method,String field,String variant) throws Exception {
        Map<String,Object> before=detail(a,m1);
        Map<String,Object> data=metadata("Valid name","Математика",false);
        if(variant.equals("missing")) data.remove(field);
        else if(variant.equals("empty")) data.put(field,"");
        else if(variant.equals("blank")) data.put(field,"   ");
        else if(variant.equals("long")) data.put(field,"А".repeat(field.equals("name")?256:field.equals("subjectName")?101:2001));
        else if(variant.equals("forbidden")) data.put(field,field.equals("sizeBytes")?1:field.equals("format")?"PDF":"2026-01-01T00:00:00Z");
        Object body=variant.equals("malformed")?"{ broken json":data;
        if(method.equals("PATCH")) patch(a,m1,body,400);
        else check("POST","/api/materials",variant.equals("malformed")?"malformed JSON":JSON.writeValueAsString(data),400,upload(a,body,"lecture.txt",TXT));
        assertEquals(before,detail(a,m1)); assertEquals(Set.of(m1,m2),ids(a));
    }
    @ParameterizedTest(name="TC-API-MAT-09: invalid upload {0}")
    @CsvSource({"no-file,400","no-metadata,400","png,415","fake-pdf,400","empty,400","bad-docx,400","bad-utf8,400","too-large,413"})
    void invalidUpload(String kind,int expected) throws Exception {
        String filename="lecture.txt"; byte[] bytes=TXT;
        if(kind.equals("no-file")) bytes=null;
        if(kind.equals("png")) { filename="image.png"; bytes=PNG; }
        if(kind.equals("fake-pdf")) { filename="lecture.pdf"; bytes=PNG; }
        if(kind.equals("empty")) bytes=new byte[0];
        if(kind.equals("bad-docx")) { filename="lecture.docx"; bytes=TXT; }
        if(kind.equals("bad-utf8")) bytes=new byte[]{(byte)0xc3,0x28};
        if(kind.equals("too-large")) { bytes=new byte[10_485_761]; Arrays.fill(bytes,(byte)'a'); }
        Map<String,Object> before=detail(a,m1);
        check("POST","/api/materials",kind+"; filename="+filename+"; bytes="+(bytes==null?"missing":bytes.length),expected,
                upload(a,kind.equals("no-metadata")?null:metadata("Invalid upload","Математика",false),filename,bytes));
        assertEquals(Set.of(m1,m2),ids(a)); assertEquals(before,detail(a,m1));
    }
    @ParameterizedTest(name="TC-API-MAT-09: malformed ID {0}") @ValueSource(strings={"GET","PATCH","DELETE"})
    void malformedId(String method) throws Exception {
        Map<String,Object> before=detail(a,m1);
        if(method.equals("PATCH")) patch(a,"not-a-uuid",Map.of("name","Valid","subjectName","Математика"),400);
        else check(method,"/api/materials/not-a-uuid","malformed UUID",400,request(a));
        assertEquals(Set.of(m1,m2),ids(a)); assertEquals(before,detail(a,m1));
    }
    @Test @DisplayName("TC-API-MAT-10: duplicate create and rename")
    void duplicateNames() throws Exception {
        Map<String,Object> before=detail(a,m2);
        check("POST","/api/materials","duplicate name=Алгебра",409,upload(a,metadata("Алгебра","Математика",false),"lecture.txt",TXT));
        patch(a,m2,Map.of("name","Алгебра","subjectName","Математика"),409);
        assertEquals(before,detail(a,m2)); assertEquals(Set.of(m1,m2),ids(a));
    }

    private static byte[] fixture(String extension) throws Exception {
        if(extension.equals("txt")) return TXT;
        var out=new ByteArrayOutputStream();
        if(extension.equals("pdf")) {
            try(var pdf=new PDDocument()) { pdf.addPage(new PDPage()); pdf.save(out); }
        } else {
            try(var zip=new ZipOutputStream(out)) {
                zipEntry(zip,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
                zipEntry(zip,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
                zipEntry(zip,"word/document.xml","<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Lecture on algebra.</w:t></w:r></w:p></w:body></w:document>");
            }
        }
        return out.toByteArray();
    }
    private static void zipEntry(ZipOutputStream zip,String name,String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
}
