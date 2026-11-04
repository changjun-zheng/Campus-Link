package com.campuslink.module.forum.web;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.PostImageApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PostImageControllerTest {
    @Test void anonymousUploadReturns401WithoutCallingApplication() {
        var images = mock(PostImageApplicationService.class);
        var controller = new PostImageController(images);
        assertThatThrownBy(() -> controller.upload(new MockMultipartFile("file", new byte[]{1}), null))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verifyNoInteractions(images);
    }
}
