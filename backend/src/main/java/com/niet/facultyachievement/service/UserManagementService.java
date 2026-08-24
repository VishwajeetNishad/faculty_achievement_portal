package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.UserCreateRequest;
import com.niet.facultyachievement.dto.UserResponse;
import com.niet.facultyachievement.dto.UserStatusUpdateRequest;
import com.niet.facultyachievement.dto.UserUpdateRequest;

/**
 * Creating, editing and activating/deactivating portal accounts.
 *
 * <p>Every method takes the acting administrator's email rather than a user id
 * or a role name. That email comes from the JWT via {@code SecurityContextHolder}
 * in the controller and is looked up fresh from the database here, so the rules
 * below are always applied against what the actor can do *right now* — not what
 * their token claimed when it was issued.
 */
public interface UserManagementService {

    UserResponse createUser(UserCreateRequest request, String actorEmail);

    UserResponse updateUser(Long userId, UserUpdateRequest request, String actorEmail);

    UserResponse updateUserStatus(Long userId, UserStatusUpdateRequest request, String actorEmail);
}
