package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.ResearchGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResearchGrantRepository extends JpaRepository<ResearchGrant, Long> {
    Optional<ResearchGrant> findByAchievementId(Long achievementId);
}
