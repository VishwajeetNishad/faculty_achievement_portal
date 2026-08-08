package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmployeeId(String employeeId);
    List<User> findByDepartmentId(Long departmentId);
    boolean existsByEmail(String email);
    boolean existsByEmployeeId(String employeeId);
}
