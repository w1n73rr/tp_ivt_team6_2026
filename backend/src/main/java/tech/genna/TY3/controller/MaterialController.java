package tech.genna.TY3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.genna.TY3.api.MaterialsApi;
import tech.genna.TY3.api.model.*;
import tech.genna.TY3.model.AllowedFileFormat;
import tech.genna.TY3.security.CurrentUser;
import tech.genna.TY3.service.material.MaterialService;
import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MaterialController implements MaterialsApi {
    private final MaterialService materials;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<AllowedFileFormatsResponse> getAllowedFileFormats() {
        var extensions = Arrays.stream(AllowedFileFormat.values())
                .map(AllowedFileFormat::extension)
                .toList();
        return ResponseEntity.ok(new AllowedFileFormatsResponse(extensions));
    }

    @Override
    public ResponseEntity<MaterialListResponse> listMaterials() {
        return ResponseEntity.ok(materials.findAll(currentUser.get()));
    }

    @Override
    public ResponseEntity<MaterialDetail> uploadMaterial(MultipartFile file, UploadMaterialMetadata metadata) {
        var material = materials.create(file, metadata, currentUser.get());
        return ResponseEntity.created(URI.create("/api/materials/" + material.getMaterial().getId())).body(material);
    }

    @Override
    public ResponseEntity<MaterialDetail> getMaterial(UUID materialId) {
        return ResponseEntity.ok(materials.get(materialId, currentUser.get()));
    }

    @Override
    public ResponseEntity<MaterialSummary> updateMaterial(UUID materialId, UpdateMaterialRequest request) {
        return ResponseEntity.ok(materials.update(materialId, request, currentUser.get()));
    }

    @Override
    public ResponseEntity<Void> deleteMaterial(UUID materialId) {
        materials.delete(materialId, currentUser.get());
        return ResponseEntity.noContent().build();
    }
}
