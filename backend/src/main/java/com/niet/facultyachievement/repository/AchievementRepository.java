package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.AchievementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    List<Achievement> findByUserId(Long userId);
    List<Achievement> findByUserIdAndStatus(Long userId, AchievementStatus status);
    List<Achievement> findByCategoryId(Long categoryId);
    List<Achievement> findByStatus(AchievementStatus status);
    List<Achievement> findByAcademicYear(String academicYear);
    List<Achievement> findByUserDepartmentId(Long departmentId);
}
