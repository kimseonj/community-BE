package kr.kakaotech.community.service;

import kr.kakaotech.community.dto.response.LikeResponse;
import kr.kakaotech.community.entity.*;
import kr.kakaotech.community.exception.CustomException;
import kr.kakaotech.community.exception.ErrorCode;
import kr.kakaotech.community.repository.LikeRepository;
import kr.kakaotech.community.repository.PostRepository;
import kr.kakaotech.community.repository.PostStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    LikeRepository likeRepository;
    @Mock
    UserLookupService userLookupService;
    @Mock
    PostRepository postRepository;
    @Mock
    PostStatusRepository postStatusRepository;

    @InjectMocks
    LikeService likeService;

    private UUID userId;
    private int postId;
    private PostStatus postStatus;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        postId = 1;

        Post post = mock(Post.class);
        postStatus = new PostStatus(post);
        ReflectionTestUtils.setField(postStatus, "postId", postId);
    }

    @Nested
    @DisplayName("좋아요 토글 (toggleLike)")
    class ToggleLike {

        @Test
        @DisplayName("좋아요 등록 - 좋아요가 없을 때 새로 생성")
        void like_success() {
            // given
            User userRef = mock(User.class);
            Post postRef = mock(Post.class);

            given(postStatusRepository.findByIdForUpdate(postId)).willReturn(Optional.of(postStatus));
            given(likeRepository.findByUser_IdAndPost_Id(userId, postId)).willReturn(Optional.empty());
            given(userLookupService.getReference(userId)).willReturn(userRef);
            given(postRepository.getReferenceById(postId)).willReturn(postRef);
            given(likeRepository.save(any(PostLike.class))).willReturn(null);
            // getLikeCount 내부 호출
            ReflectionTestUtils.setField(postStatus, "likeCount", 1);
            given(postStatusRepository.findById(postId)).willReturn(Optional.of(postStatus));

            // when
            LikeResponse response = likeService.toggleLike(userId, postId);

            // then
            assertThat(response.isLikeStatus()).isTrue();
            assertThat(response.getLikeCount()).isEqualTo(1);

            verify(likeRepository).save(any(PostLike.class));
            verify(postStatusRepository).incrementLikeCount(postId);
            verify(likeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("좋아요 취소 - 좋아요가 있을 때 삭제")
        void unlike_success() {
            // given
            PostLike existingLike = mock(PostLike.class);

            given(postStatusRepository.findByIdForUpdate(postId)).willReturn(Optional.of(postStatus));
            given(likeRepository.findByUser_IdAndPost_Id(userId, postId)).willReturn(Optional.of(existingLike));
            // getLikeCount 내부 호출
            ReflectionTestUtils.setField(postStatus, "likeCount", 0);
            given(postStatusRepository.findById(postId)).willReturn(Optional.of(postStatus));

            // when
            LikeResponse response = likeService.toggleLike(userId, postId);

            // then
            assertThat(response.isLikeStatus()).isFalse();
            assertThat(response.getLikeCount()).isEqualTo(0);

            verify(likeRepository).delete(existingLike);
            verify(postStatusRepository).decrementLikeCount(postId);
            verify(likeRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 유저 또는 게시글 (FK 위반)")
        void fail_invalidUserOrPost() {
            // given
            User userRef = mock(User.class);
            Post postRef = mock(Post.class);

            given(postStatusRepository.findByIdForUpdate(postId)).willReturn(Optional.of(postStatus));
            given(likeRepository.findByUser_IdAndPost_Id(userId, postId)).willReturn(Optional.empty());
            given(userLookupService.getReference(userId)).willReturn(userRef);
            given(postRepository.getReferenceById(postId)).willReturn(postRef);
            given(likeRepository.save(any(PostLike.class)))
                    .willThrow(new DataIntegrityViolationException("FK constraint violation"));

            // when & then
            assertThatThrownBy(() -> likeService.toggleLike(userId, postId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_FOUND_POST));
        }
    }

    @Nested
    @DisplayName("좋아요 상태 조회 (getLikeStatus)")
    class GetLikeStatus {

        @Test
        @DisplayName("로그인 사용자 - 좋아요 O")
        void loggedIn_liked() {
            // given
            Optional<UUID> optionalUserId = Optional.of(userId);
            given(likeRepository.findByUser_IdAndPost_Id(userId, postId))
                    .willReturn(Optional.of(mock(PostLike.class)));

            // when
            boolean result = likeService.getLikeStatus(optionalUserId, postId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("로그인 사용자 - 좋아요 X")
        void loggedIn_notLiked() {
            // given
            Optional<UUID> optionalUserId = Optional.of(userId);
            given(likeRepository.findByUser_IdAndPost_Id(userId, postId))
                    .willReturn(Optional.empty());

            // when
            boolean result = likeService.getLikeStatus(optionalUserId, postId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("비로그인 사용자 - 항상 false")
        void notLoggedIn() {
            // given
            Optional<UUID> optionalUserId = Optional.empty();

            // when
            boolean result = likeService.getLikeStatus(optionalUserId, postId);

            // then
            assertThat(result).isFalse();
            verify(likeRepository, never()).findByUser_IdAndPost_Id(any(), any());
        }
    }

    @Nested
    @DisplayName("좋아요 수 조회 (getLikeCount)")
    class GetLikeCount {

        @Test
        @DisplayName("성공 - PostStatus에서 좋아요 수 반환")
        void success() {
            // given
            ReflectionTestUtils.setField(postStatus, "likeCount", 42);
            given(postStatusRepository.findById(postId)).willReturn(Optional.of(postStatus));

            // when
            int count = likeService.getLikeCount(postId);

            // then
            assertThat(count).isEqualTo(42);
        }
    }
}
