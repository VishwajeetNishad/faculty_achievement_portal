package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.Patent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatentRepository extends JpaRepository<Patent, Long> {
    Optional<Patent> findByAchievementId(Long achievementId);
}
