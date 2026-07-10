package kr.kakaotech.community.service;

import kr.kakaotech.community.dto.response.LikeResponse;
import kr.kakaotech.community.entity.Post;
import kr.kakaotech.community.entity.PostLike;
import kr.kakaotech.community.entity.User;
import kr.kakaotech.community.exception.CustomException;
import kr.kakaotech.community.exception.ErrorCode;
import kr.kakaotech.community.repository.LikeRepository;
import kr.kakaotech.community.repository.PostRepository;
import kr.kakaotech.community.repository.PostStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final UserLookupService userLookupService;
    private final PostRepository postRepository;
    private final PostStatusRepository postStatusRepository;

    @Transactional
    public LikeResponse toggleLike(UUID userId, int postId) {
        // PostStatus 행에 PESSIMISTIC_WRITE 락을 획득해 같은 게시글에 대한 동시 토글을 직렬화한다.
        // 가용성 문제(StaleObjectStateException 다발) 해결이 목적.
        postStatusRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_POST));

        Optional<PostLike> optionalPostLike = likeRepository.findByUser_IdAndPost_Id(userId, postId);

        // 좋아요 취소
        if (optionalPostLike.isPresent()) {
            likeRepository.delete(optionalPostLike.get());
            postStatusRepository.decrementLikeCount(postId);

            return new LikeResponse(false, getLikeCount(postId));
        }

        // 좋아요 등록
        // Reference 객체 사용해서 select 문 안날아가게 함
        try {
            // getReferenceById 사용 - 성능 최적화
            User userRef = userLookupService.getReference(userId);
            Post postRef = postRepository.getReferenceById(postId);

            PostLike newLike = new PostLike(userRef, postRef);
            likeRepository.save(newLike);

            postStatusRepository.incrementLikeCount(postId);

            return new LikeResponse(true, getLikeCount(postId));

        } catch (DataIntegrityViolationException e) {
            // FK 제약조건 위반
            log.error("Invalid user or post. userId={}, postId={}", userId, postId);
            throw new CustomException(ErrorCode.NOT_FOUND_POST);
        }
    }

    /**
     * 좋아요 상태 가져오기
     */
    @Transactional(readOnly = true)
    public boolean getLikeStatus(Optional<UUID> optionalUserId, int postId) {
        if (optionalUserId.isEmpty()) return false;

        return likeRepository.findByUser_IdAndPost_Id(optionalUserId.get(), postId).isPresent();
    }

    /**
     * 좋아요 갯수 세기
     */
    @Transactional(readOnly = true)
    public int getLikeCount(int postId) {
        return postStatusRepository.findById(postId).get().getLikeCount();
    }
}
