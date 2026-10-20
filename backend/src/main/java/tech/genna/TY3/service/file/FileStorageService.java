package tech.genna.TY3.service.file;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import tech.genna.TY3.dto.file.FileUploadResponse;
import tech.genna.TY3.model.exception.FileStorageException;
import tech.genna.TY3.model.exception.ResourceNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class FileStorageService {
    public enum FileScope {
        GENERAL(""), MATERIALS("materials/");

        private final String prefix;

        FileScope(String prefix) {
            this.prefix = prefix;
        }
    }

    private final S3Client s3Client;
    private final String bucketName;

    public FileStorageService(S3Client s3Client, @Value("${s3.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @PostConstruct
    public void initBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception e) {
            // HEAD may return a generic S3Exception for a missing bucket.
            if (e.statusCode() != 404) {
                throw new FileStorageException("Не удалось проверить хранилище файлов", e);
            }
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            } catch (BucketAlreadyOwnedByYouException ignored) {
                // Another application instance created the bucket concurrently.
            } catch (SdkException ex) {
                throw new FileStorageException("Не удалось создать хранилище файлов", ex);
            }
        } catch (SdkException e) {
            throw new FileStorageException("Не удалось подключиться к хранилищу файлов", e);
        }
    }

    public FileUploadResponse uploadFile(MultipartFile file, UUID ownerId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }
        try (InputStream input = file.getInputStream()) {
            return uploadFile(input, file.getSize(), file.getOriginalFilename(), file.getContentType(),
                    UUID.randomUUID(), ownerId, FileScope.GENERAL);
        } catch (IOException e) {
            throw new FileStorageException("Не удалось загрузить файл в хранилище", e);
        }
    }

    /** The caller owns the stream. A preallocated id allows durable upload compensation. */
    public FileUploadResponse uploadFile(InputStream input, long size, String filename, String contentType,
                                         UUID fileId, UUID ownerId, FileScope scope) {
        if (input == null || size <= 0) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }
        String key = objectKey(ownerId, fileId, scope);
        if (filename == null || filename.isBlank()) {
            filename = "file";
        }
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName).key(key).contentType(contentType).build(),
                    RequestBody.fromInputStream(input, size));
        } catch (SdkException e) {
            throw new FileStorageException("Не удалось загрузить файл в хранилище", e);
        }
        return new FileUploadResponse(fileId, key, filename, contentType, size);
    }

    /** The caller must close the returned stream after reading it. */
    public ResponseInputStream<GetObjectResponse> readFile(UUID fileId, UUID ownerId) {
        return readFile(fileId, ownerId, FileScope.GENERAL);
    }

    public ResponseInputStream<GetObjectResponse> readFile(UUID fileId, UUID ownerId, FileScope scope) {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName).key(objectKey(ownerId, fileId, scope)).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404 && !(e instanceof NoSuchBucketException)) {
                throw new ResourceNotFoundException("Файл не найден");
            }
            throw new FileStorageException("Не удалось прочитать файл из хранилища", e);
        } catch (SdkException e) {
            throw new FileStorageException("Не удалось прочитать файл из хранилища", e);
        }
    }

    /** Idempotent deletion restricted to the authenticated user's namespace. */
    public void deleteFile(UUID fileId, UUID ownerId) {
        deleteFile(fileId, ownerId, FileScope.GENERAL);
    }

    public void deleteFile(UUID fileId, UUID ownerId, FileScope scope) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName).key(objectKey(ownerId, fileId, scope)).build());
        } catch (SdkException e) {
            throw new FileStorageException("Не удалось удалить файл из хранилища", e);
        }
    }

    private String objectKey(UUID ownerId, UUID fileId) {
        return objectKey(ownerId, fileId, FileScope.GENERAL);
    }

    private String objectKey(UUID ownerId, UUID fileId, FileScope scope) {
        if (ownerId == null || fileId == null) {
            throw new IllegalArgumentException("Не указан идентификатор пользователя или файла");
        }
        if (scope == null) throw new IllegalArgumentException("Не указана область хранения файла");
        return scope.prefix + ownerId + "/" + fileId;
    }
}
