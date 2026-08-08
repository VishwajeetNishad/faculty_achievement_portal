package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.Award;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AwardRepository extends JpaRepository<Award, Long> {
    Optional<Award> findByAchievementId(Long achievementId);
}
