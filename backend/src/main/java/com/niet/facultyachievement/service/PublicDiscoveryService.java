package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.DepartmentResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.dto.publicview.PublicAchievementResponse;
import com.niet.facultyachievement.dto.publicview.PublicFacultyProfileResponse;
import com.niet.facultyachievement.dto.publicview.PublicFacultyResponse;

import java.util.List;

/**
 * Everything an anonymous visitor is allowed to read.
 *
 * <p>Every method here answers requests that arrive with no token, no session
 * and no identity. There is therefore no "who is asking?" parameter anywhere in
 * this interface, and no method takes a status or visibility filter — the rule
 * {@code status = APPROVED AND visibility = PUBLIC} is fixed inside the queries
 * and cannot be influenced by a caller.
 *
 * <p>Share links are deliberately <strong>not</strong> here. They live in
 * {@link ShareService} because they answer a different question: this interface
 * is "what has been published to everyone", a share link is "what one person
 * decided to show one recipient". Mixing them would put the only code path that
 * can return unapproved work next to the code path that must never return any.
 */
public interface PublicDiscoveryService {

    /**
     * The public faculty directory.
     *
     * @param keyword        matched against name, designation and department; null for no filter
     * @param departmentCode e.g. {@code CSE}; null for all departments
     */
    PagedResponse<PublicFacultyResponse> searchFaculty(String keyword, String departmentCode, int page, int size);

    /**
     * One person's public profile header.
     *
     * @throws com.niet.facultyachievement.exception.ResourceNotFoundException
     *         if the slug is unknown, the account is inactive, or the person has
     *         nothing publicly visible — all three are the same answer to a
     *         visitor, which is deliberate: distinguishing them would confirm
     *         that an account exists.
     */
    PublicFacultyProfileResponse getFacultyProfile(String slug);

    /** One person's publicly visible achievements. */
    PagedResponse<PublicAchievementResponse> getFacultyAchievements(String slug, String categoryCode, int page, int size);

    /** The public research gallery across all faculty. */
    PagedResponse<PublicAchievementResponse> searchAchievements(String keyword, String categoryCode,
                                                                String departmentCode, int page, int size);

    /** Departments, for the filter dropdown on the public pages. */
    List<DepartmentResponse> getDepartments();
}
