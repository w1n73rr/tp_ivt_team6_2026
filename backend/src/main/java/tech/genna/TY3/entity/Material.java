package tech.genna.TY3.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "materials")
@Getter
@Setter
@NoArgsConstructor
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(length = 2000)
    private String description;
    @Column(name = "file_id", nullable = false, unique = true)
    private UUID fileId;
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;
    @Column(name = "file_type", nullable = false, length = 10)
    private String fileType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 20)
    private String status = "STORED";
    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @ManyToMany
    @JoinTable(name = "material_tags", joinColumns = @JoinColumn(name = "material_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();
}
