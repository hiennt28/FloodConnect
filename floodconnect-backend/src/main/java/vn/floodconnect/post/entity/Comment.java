package vn.floodconnect.post.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "comments")
@Getter @Setter @NoArgsConstructor
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User author;
    /** Bình luận cấp 1 được trả lời; null nếu chính nó là cấp 1. */
    @ManyToOne(fetch = FetchType.LAZY) private Comment parentComment;
    @ManyToOne(fetch = FetchType.LAZY) private User replyToUser;
    @Column(nullable = false, length = 1000) private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContentStatus status = ContentStatus.VISIBLE;
    private boolean edited;
    private Instant deletedAt;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
}
