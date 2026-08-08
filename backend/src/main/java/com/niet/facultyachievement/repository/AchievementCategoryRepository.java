package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.AchievementCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AchievementCategoryRepository extends JpaRepository<AchievementCategory, Long> {
    Optional<AchievementCategory> findByCode(String code);
    List<AchievementCategory> findByIsActiveTrue();
}
