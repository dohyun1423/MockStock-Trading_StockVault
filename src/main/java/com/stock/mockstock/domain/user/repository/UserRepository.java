// 회원 DB 조회를 담당하는 Repository
package com.stock.mockstock.domain.user.repository;

import com.stock.mockstock.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // email 기준으로 사용자를 조회한다.
    Optional<User> findByEmail(String email);

    // 주문 생성/수정/취소처럼 현금이 바뀌는 흐름에서는 사용자 행을 쓰기 락으로 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    // email 중복 여부를 확인한다.
    boolean existsByEmail(String email);

    // 닉네임 중복 여부를 확인한다.
    boolean existsByNickname(String nickname);

    // 내 닉네임을 제외하고 같은 닉네임을 쓰는 사용자가 있는지 확인한다.
    boolean existsByNicknameAndEmailNot(String nickname, String email);
}
