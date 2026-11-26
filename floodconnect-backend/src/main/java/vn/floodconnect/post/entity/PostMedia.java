package vn.floodconnect.post.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity @Table(name = "post_media")
@Getter @Setter @NoArgsConstructor
public class PostMedia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Post post;
    @Column(nullable = false) private String mediaUrl;
    @Column(length = 30) private String mediaType;
    private int sortOrder;
    @CreationTimestamp private Instant createdAt;
}
