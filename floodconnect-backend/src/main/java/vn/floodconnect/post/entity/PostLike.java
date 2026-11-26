package vn.floodconnect.post.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.io.Serializable;
import java.time.Instant;

/** Khóa chính là cặp (post_id, user_id). */
@Entity @Table(name = "post_likes") @IdClass(PostLike.Key.class)
@Getter @Setter @NoArgsConstructor
public class PostLike {
    @Id private Long postId;
    @Id private Long userId;
    @CreationTimestamp private Instant createdAt;

    public PostLike(Long postId, Long userId) { this.postId = postId; this.userId = userId; }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable { private Long postId; private Long userId; }
}
