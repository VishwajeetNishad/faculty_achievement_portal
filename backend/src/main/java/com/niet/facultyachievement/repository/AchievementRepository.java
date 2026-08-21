package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.dto.dashboard.AcademicYearStatDTO;
import com.niet.facultyachievement.dto.dashboard.CategoryStatDTO;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.AchievementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long>,
        JpaSpecificationExecutor<Achievement> {
    List<Achievement> findByUserId(Long userId);
    List<Achievement> findByUserIdAndStatus(Long userId, AchievementStatus status);
    List<Achievement> findByCategoryId(Long categoryId);
    List<Achievement> findByStatus(AchievementStatus status);
    List<Achievement> findByAcademicYear(String academicYear);
    List<Achievement> findByUserDepartmentId(Long departmentId);

    // --- Aggregate Counts ---
    long countByUserId(Long userId);
    long countByUserIdAndStatus(Long userId, AchievementStatus status);

    long countByUserDepartmentId(Long departmentId);
    long countByUserDepartmentIdAndStatus(Long departmentId, AchievementStatus status);

    long countByStatus(AchievementStatus status);

    // --- Top 5 Recent Achievements ---
    List<Achievement> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);
    List<Achievement> findTop5ByUserDepartmentIdOrderByCreatedAtDesc(Long departmentId);

    // --- Category Analytics Aggregations ---
    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.CategoryStatDTO(a.category.categoryName, COUNT(a)) FROM Achievement a WHERE a.user.id = :userId GROUP BY a.category.categoryName")
    List<CategoryStatDTO> getCategoryStatsByUserId(@Param("userId") Long userId);

    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.CategoryStatDTO(a.category.categoryName, COUNT(a)) FROM Achievement a WHERE a.user.department.id = :departmentId GROUP BY a.category.categoryName")
    List<CategoryStatDTO> getCategoryStatsByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.CategoryStatDTO(a.category.categoryName, COUNT(a)) FROM Achievement a GROUP BY a.category.categoryName")
    List<CategoryStatDTO> getOverallCategoryStats();

    // --- Academic Year Analytics Aggregations ---
    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.AcademicYearStatDTO(a.academicYear, COUNT(a)) FROM Achievement a WHERE a.user.id = :userId GROUP BY a.academicYear")
    List<AcademicYearStatDTO> getAcademicYearStatsByUserId(@Param("userId") Long userId);

    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.AcademicYearStatDTO(a.academicYear, COUNT(a)) FROM Achievement a WHERE a.user.department.id = :departmentId GROUP BY a.academicYear")
    List<AcademicYearStatDTO> getAcademicYearStatsByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.AcademicYearStatDTO(a.academicYear, COUNT(a)) FROM Achievement a GROUP BY a.academicYear")
    List<AcademicYearStatDTO> getOverallAcademicYearStats();
}
