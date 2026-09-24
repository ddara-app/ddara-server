package com.app.backend.domain.user.service;

import com.app.backend.domain.auth.apple.AppleAuthClient;
import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.block.repository.BlockRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.group.service.GroupService;
import com.app.backend.domain.notification.repository.NotificationRepository;
import com.app.backend.domain.upload.service.UploadService;
import com.app.backend.domain.user.dto.CameraGuideResponse;
import com.app.backend.domain.user.dto.NotificationSettingsRequest;
import com.app.backend.domain.user.dto.NotificationSettingsResponse;
import com.app.backend.domain.user.dto.ProfileImageResponse;
import com.app.backend.domain.user.dto.UserInfoResponse;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    // 탈퇴 후 데이터 보존 기간(일). 이 기간이 지나면 완전 삭제한다. (U-05)
    private static final long WITHDRAWAL_RETENTION_DAYS = 5;

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationRepository notificationRepository;
    private final MembershipRepository membershipRepository;
    private final BlockRepository blockRepository;
    private final GroupService groupService;
    private final AppleAuthClient appleAuthClient;
    private final UploadService uploadService;
    // 프로필 이미지로 허용할 S3 URL 접두사 (우리 버킷의 profiles/ 경로만)
    private final String profileImageUrlPrefix;

    public UserService(UserRepository userRepository,
                       ObjectMapper objectMapper,
                       RefreshTokenRepository refreshTokenRepository,
                       NotificationRepository notificationRepository,
                       MembershipRepository membershipRepository,
                       BlockRepository blockRepository,
                       GroupService groupService,
                       AppleAuthClient appleAuthClient,
                       UploadService uploadService,
                       @Value("${aws.s3.bucket}") String bucket,
                       @Value("${aws.s3.region}") String region) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.refreshTokenRepository = refreshTokenRepository;
        this.notificationRepository = notificationRepository;
        this.membershipRepository = membershipRepository;
        this.blockRepository = blockRepository;
        this.groupService = groupService;
        this.appleAuthClient = appleAuthClient;
        this.uploadService = uploadService;
        this.profileImageUrlPrefix =
                "https://" + bucket + ".s3." + region + ".amazonaws.com/profiles/";
    }

    @Transactional(readOnly = true)
    public UserInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        // 탈퇴한(soft delete) 사용자는 남은 access token으로도 조회 불가 — 없는 사용자로 취급
        if (user.isWithdrawn()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return UserInfoResponse.from(user);
    }

    @Transactional
    public ProfileImageResponse updateProfileImage(Long userId, String imageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String oldImageUrl = user.getProfileImageUrl();

        // imageUrl 없음 → 디폴트 아바타로 초기화
        if (imageUrl == null || imageUrl.isBlank()) {
            user.updateProfileImage(null);
            uploadService.deleteImage(oldImageUrl);
            return new ProfileImageResponse(null);
        }

        // 보안: 우리 S3 버킷의 profiles/ 경로 URL만 허용 (임의 URL 저장 방지)
        if (!imageUrl.startsWith(profileImageUrlPrefix)) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FILE);
        }

        user.updateProfileImage(imageUrl);
        if (!imageUrl.equals(oldImageUrl)) {
            uploadService.deleteImage(oldImageUrl);
        }
        return new ProfileImageResponse(imageUrl);
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse getNotificationSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String prefs = user.getNotificationPrefs();
        if (prefs == null || prefs.isBlank()) {
            return NotificationSettingsResponse.allOn();   // 미설정 = 전체 on
        }
        try {
            return NotificationSettingsResponse.fromJson(objectMapper.readTree(prefs));
        } catch (JsonProcessingException e) {
            return NotificationSettingsResponse.allOn();   // 깨진 값이면 기본값
        }
    }

    /** 본 카메라 가이드 파트 키 목록 조회 */
    @Transactional(readOnly = true)
    public CameraGuideResponse getCameraGuideSeen(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return new CameraGuideResponse(parseGuidesSeen(user.getCameraGuidesSeen()));
    }

    /** 카메라 가이드 파트 열람 기록 */
    @Transactional
    public void markCameraGuideSeen(Long userId, String key) {
        if (key == null || key.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        List<String> seen = parseGuidesSeen(user.getCameraGuidesSeen());
        if (!seen.contains(key)) {
            seen.add(key);
            try {
                user.updateCameraGuidesSeen(objectMapper.writeValueAsString(seen));
            } catch (JsonProcessingException e) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
        }
    }

    /** JSON 문자열을 키 목록으로 파싱 */
    private List<String> parseGuidesSeen(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(
            Long userId, NotificationSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        NotificationSettingsRequest.Activity activity = request.activity();
        NotificationSettingsRequest.Etc etc = request.etc();
        NotificationSettingsResponse normalized = new NotificationSettingsResponse(
                orTrue(request.allowAll()),
                new NotificationSettingsResponse.Activity(
                        orTrue(activity == null ? null : activity.followShot()),
                        orTrue(activity == null ? null : activity.friendShot()),
                        orTrue(activity == null ? null : activity.starterAssigned()),
                        orTrue(activity == null ? null : activity.comment()),
                        orTrue(activity == null ? null : activity.chat())),
                new NotificationSettingsResponse.Etc(
                        orTrue(etc == null ? null : etc.memberJoin())));

        try {
            user.updateNotificationPrefs(objectMapper.writeValueAsString(normalized));
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        return normalized;
    }

    private static boolean orTrue(Boolean value) {
        return value == null ? true : value;
    }

    /** FCM 토큰 등록(U-06). 유저당 1개 — 재등록 시 덮어쓴다. */
    @Transactional
    public void registerFcmToken(Long userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.updateFcmToken(fcmToken);
    }

    /** FCM 토큰 제거(U-06). 로그아웃 시 auth 도메인(오지원)에서 호출하는 연동 지점. */
    @Transactional
    public void clearFcmToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.clearFcmToken();
    }

    @Transactional
    public void withdraw(Long userId, String appleAuthorizationCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 애플 로그인 유저는 탈퇴 시 애플 연동 해제(revoke). 실패해도 탈퇴는 계속 진행.(App Store 심사 규정)
        if (user.getProvider() == AuthProvider.APPLE
                && appleAuthorizationCode != null && !appleAuthorizationCode.isBlank()) {
            try {
                appleAuthClient.revoke(appleAuthClient.exchangeCode(appleAuthorizationCode));
            } catch (Exception e) {
                log.warn("애플 연동 해제 실패(탈퇴는 계속 진행): userId={}", userId, e);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        // 익명화 시 URL이 지워지므로 프로필 이미지는 탈퇴 시점에 S3에서 삭제 (재가입은 새 계정이라 복구 불필요)
        uploadService.deleteImage(user.getProfileImageUrl());
        // soft delete + 익명화 + provider_id 자리 비움(재가입 가능). 데이터(알림·멤버십)는 5일 보존.
        user.withdraw(now);
        // 속한 모든 모임에서 나간 것으로 처리 — 멤버 목록·회차 참여 인원에서 제외되고,
        // 마지막 멤버였다면 모임도 자동 삭제된다. (모임 나가기와 동일 규칙)
        groupService.leaveAllGroupsOnWithdrawal(userId, now);
        // refresh token은 보안상 즉시 폐기(세션 종료)
        refreshTokenRepository.deleteByUserId(userId);
    }

    /**
     * 탈퇴 후 보존기간({@value #WITHDRAWAL_RETENTION_DAYS}일)이 지난 사용자를 완전 삭제한다.
     * 사용자 행과 함께 남아 있던 알림·멤버십도 물리 삭제한다. (스케줄러가 매일 호출 — U-05)
     */
    @Transactional
    public void purgeWithdrawnUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(WITHDRAWAL_RETENTION_DAYS);
        for (User user : userRepository.findByDeletedAtBefore(cutoff)) {
            notificationRepository.deleteByUserId(user.getId());
            membershipRepository.deleteByUserId(user.getId());
            blockRepository.deleteByBlockerIdOrBlockedId(user.getId(), user.getId());
            userRepository.delete(user);
        }
    }
}