// 사용자 비밀번호 변경 이력을 조회하고 저장하는 Repository
package com.stock.mockstock.domain.user.repository;

import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.entity.UserPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPasswordHistoryRepository extends JpaRepository<UserPasswordHistory, Long> {

    // 특정 사용자가 이전에 사용한 비밀번호 해시 목록을 조회한다.
    List<UserPasswordHistory> findAllByUser(User user);
}
