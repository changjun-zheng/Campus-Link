CREATE TABLE post_images (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    post_id BIGINT NULL,
    in_use BOOLEAN NOT NULL DEFAULT FALSE,
    state VARCHAR(16) NOT NULL,
    storage_key VARCHAR(48) NOT NULL,
    mime_type VARCHAR(32) NOT NULL,
    byte_size INT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_post_images_storage (storage_key),
    INDEX idx_post_images_owner (owner_id, created_at),
    INDEX idx_post_images_post (post_id, in_use)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
