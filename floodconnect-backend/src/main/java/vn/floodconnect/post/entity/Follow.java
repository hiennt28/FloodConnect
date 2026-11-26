package vn.floodconnect.post.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.io.Serializable;
import java.time.Instant;

@Entity @Table(name = "follows") @IdClass(Follow.Key.class)
@Getter @Setter @NoArgsConstructor
public class Follow {
    @Id private Long followerId;
    @Id private Long followingId;
    @CreationTimestamp private Instant createdAt;

    public Follow(Long followerId, Long followingId) { this.followerId = followerId; this.followingId = followingId; }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable { private Long followerId; private Long followingId; }
}
