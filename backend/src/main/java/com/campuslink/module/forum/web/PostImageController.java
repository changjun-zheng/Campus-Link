package com.campuslink.module.forum.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.common.web.PublicEndpoint;
import com.campuslink.module.forum.application.PostImageApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/post-images")
@Tag(name = "post-image", description = "帖子图片上传与受控读取")
@RequiredArgsConstructor
public class PostImageController {
    private final PostImageApplicationService images;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传帖子图片；仅登录且可发帖账号，JPEG/PNG ≤2MiB")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.USER_BANNED,
            ResultCode.USER_MUTED, ResultCode.ACCOUNT_DEACTIVATED, ResultCode.FILE_TOO_LARGE,
            ResultCode.POST_IMAGE_INVALID, ResultCode.POST_IMAGE_RATE_LIMITED,
            ResultCode.POST_IMAGE_STORAGE_UNAVAILABLE})
    public ApiResponse<PostImageApplicationService.UploadedImage> upload(
            @RequestParam("file") MultipartFile file, Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        if (file.getSize() > 2L * 1024 * 1024) {
            throw new ApiException(ResultCode.FILE_TOO_LARGE);
        }
        try {
            return ApiResponse.ok(images.upload(userId, file.getBytes()));
        } catch (IOException ex) {
            throw new ApiException(ResultCode.POST_IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    @GetMapping(value = "/{id}/content", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
    @PublicEndpoint
    @Operation(summary = "图片内容：草稿仅本人；引用图片跟随帖子可见性，不缓存")
    @ErrorCodes({ResultCode.NOT_FOUND, ResultCode.USER_BANNED, ResultCode.ACCOUNT_DEACTIVATED})
    public ResponseEntity<byte[]> content(@PathVariable UUID id, Authentication authentication) {
        var content = images.read(id, CurrentUser.optionalId(authentication));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mimeType()))
                .header("Cache-Control", "no-store").header("Vary", "Authorization")
                .header("X-Content-Type-Options", "nosniff").body(content.bytes());
    }
}
