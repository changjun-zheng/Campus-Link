package com.campuslink.module.forum.infrastructure.persistence;

import com.campuslink.module.forum.domain.gateway.PostImageRepository;
import com.campuslink.module.forum.domain.model.PostImage;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcPostImageRepository implements PostImageRepository {
    private final JdbcTemplate jdbc;

    @Override
    public void reserve(PostImage image) {
        jdbc.update("""
                INSERT INTO post_images
                (id, owner_id, state, storage_key, mime_type, byte_size, width, height)
                VALUES (?, ?, 'UPLOADING', ?, ?, ?, ?, ?)
                """, image.id().toString(), image.ownerId(), image.storageKey(), image.mimeType(),
                image.size(), image.width(), image.height());
    }

    @Override
    public void state(UUID id, String state) {
        jdbc.update("UPDATE post_images SET state=? WHERE id=? AND state='UPLOADING'", state, id.toString());
    }

    @Override
    public Optional<PostImage> find(UUID id) {
        return select(id, false);
    }

    @Override
    public Optional<PostImage> lock(UUID id) {
        return select(id, true);
    }

    private Optional<PostImage> select(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM post_images WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, index) -> new PostImage(UUID.fromString(rs.getString("id")), rs.getLong("owner_id"),
                        rs.getObject("post_id", Long.class), rs.getBoolean("in_use"), rs.getString("state"),
                        rs.getString("storage_key"), rs.getString("mime_type"), rs.getInt("byte_size"),
                        rs.getInt("width"), rs.getInt("height"), rs.getTimestamp("created_at").toInstant()),
                id.toString()).stream().findFirst();
    }

    @Override
    public void reference(UUID id, long postId) {
        jdbc.update("UPDATE post_images SET post_id=?, in_use=TRUE WHERE id=?", postId, id.toString());
    }

    @Override
    public void clearReferences(long postId) {
        // Bind and this reset share the caller's post transaction and row lock.
        jdbc.update("UPDATE post_images SET in_use=FALSE WHERE post_id=?", postId);
    }
}
