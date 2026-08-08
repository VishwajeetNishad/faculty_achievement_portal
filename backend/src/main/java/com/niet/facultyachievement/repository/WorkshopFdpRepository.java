package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.WorkshopFdp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkshopFdpRepository extends JpaRepository<WorkshopFdp, Long> {
    Optional<WorkshopFdp> findByAchievementId(Long achievementId);
}
