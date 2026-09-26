package com.app.backend.domain.group.repository;

import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, MembershipId> {

    // 특정 모임의 내 멤버십 (나간 것 포함) — 재참여 복귀 판단용
    Optional<Membership> findByGroupIdAndUserId(Long groupId, Long userId);

    // 한 모임에서 여러 사용자의 멤버십 (나간 것 포함) — 메시지 작성자 닉네임 조회용
    List<Membership> findByGroupIdAndUserIdIn(Long groupId, Collection<Long> userIds);

    // 내가 현재 속한 멤버십 목록
    List<Membership> findByUserIdAndLeftAtIsNull(Long userId);

    // 한 모임의 현재 멤버 목록
    List<Membership> findByGroupIdAndLeftAtIsNull(Long groupId);

    // 한 모임의 현재 멤버 중 가입 순 앞 2명 (미리보기 아바타용)
    List<Membership> findTop2ByGroupIdAndLeftAtIsNullOrderByJoinedAtAsc(Long groupId);

    // 한 모임의 현재 멤버 수
    long countByGroupIdAndLeftAtIsNull(Long groupId);

    // 내가 현재 속한 모임 수
    long countByUserIdAndLeftAtIsNull(Long userId);

    // 내가 이 모임에 이미 참여 중인지
    boolean existsByGroupIdAndUserIdAndLeftAtIsNull(Long groupId, Long userId);

    // 모임 현재 멤버 중 같은 닉네임이 있는지 (중복 방지)
    boolean existsByGroupIdAndNicknameAndLeftAtIsNull(Long groupId, String nickname);

    // 모임 완전 삭제 시 멤버십 일괄 삭제
    void deleteByGroupId(Long groupId);

    // 탈퇴 사용자 완전 삭제 시 그 사용자의 멤버십 일괄 삭제 (U-05)
    void deleteByUserId(Long userId);
}