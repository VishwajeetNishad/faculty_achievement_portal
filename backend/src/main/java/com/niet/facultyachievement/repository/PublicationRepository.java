package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.Publication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {
    Optional<Publication> findByAchievementId(Long achievementId);
}
