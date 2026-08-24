package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.DepartmentResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.dto.publicview.PublicAchievementResponse;
import com.niet.facultyachievement.dto.publicview.PublicFacultyProfileResponse;
import com.niet.facultyachievement.dto.publicview.PublicFacultyResponse;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.AchievementRepository;
import com.niet.facultyachievement.repository.DepartmentRepository;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The public read side of the portal.
 *
 * <p>Three habits run through this whole class, and they are the reason it is
 * separate from {@code AchievementServiceImpl} rather than a few extra methods
 * on it:
 *
 * <ol>
 *   <li><strong>Every method is read-only.</strong> Nothing an anonymous
 *       visitor does can write to the database. {@code @Transactional(readOnly
 *       = true)} says so at the boundary as well as in intent.</li>
 *   <li><strong>The visibility rule is not here.</strong> It is compiled into
 *       the repository queries as JPQL literals. This class cannot relax it even
 *       by accident, because it never states it.</li>
 *   <li><strong>Public DTOs only.</strong> Nothing in this file can return an
 *       {@code AchievementResponse} or a {@code UserResponse}; those carry
 *       emails, employee ids and reviewer comments. The mapping functions used
 *       here physically cannot produce those fields.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PublicDiscoveryServiceImpl implements PublicDiscoveryService {

    private final UserRepository userRepository;
    private final AchievementRepository achievementRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * Upper bound on {@code size}. An endpoint with no authentication in front of
     * it should not let a stranger ask for a hundred thousand rows in one call;
     * that is a free denial-of-service. The public pages ask for 200–500, so 500
     * is generous for real use and still cheap to serve.
     */
    private static final int MAX_PAGE_SIZE = 500;

    /* ================================================================
       Faculty directory
       ================================================================ */

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PublicFacultyResponse> searchFaculty(String keyword, String departmentCode,
                                                              int page, int size) {
        Pageable pageable = pageRequest(page, size, Sort.by(Sort.Direction.ASC, "fullName"));

        Page<User> people = userRepository.searchPublicFaculty(
                blankToNull(keyword),
                blankToNull(departmentCode),
                pageable
        );

        /* One grouped query for the counts of everyone on this page, rather than
           two counts per person. Loaded once, then read from the map. */
        Map<Long, long[]> counts = publicCountsByUserId();

        return PagedResponse.from(people, user -> {
            long[] pair = counts.getOrDefault(user.getId(), new long[]{0L, 0L});
            return PublicFacultyResponse.fromEntity(user, pair[0], pair[1]);
        });
    }

    /**
     * {@code userId -> [totalPublic, publicPublications]}.
     *
     * <p>Reads a grouped query into a map. The raw rows come back as
     * {@code Object[]} with numeric types that vary by database and by whether
     * the value came from {@code COUNT} or {@code SUM}, so each cell goes through
     * {@link #toLong(Object)} rather than being cast.
     */
    private Map<Long, long[]> publicCountsByUserId() {
        List<Object[]> rows = achievementRepository.countPublicAchievementsGroupedByUser();
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : rows) {
            Long userId = toLong(row[0]);
            if (userId == null) continue;
            counts.put(userId, new long[]{toLongOrZero(row[1]), toLongOrZero(row[2])});
        }
        return counts;
    }

    /* ================================================================
       One person's public profile
       ================================================================ */

    @Override
    @Transactional(readOnly = true)
    public PublicFacultyProfileResponse getFacultyProfile(String slug) {
        String cleanSlug = blankToNull(slug);
        if (cleanSlug == null) {
            throw new ResourceNotFoundException("Faculty profile not found");
        }

        /* One message for every reason this can fail — unknown slug, inactive
           account, nothing published. Naming the actual reason would confirm
           that an account exists, which is exactly what a visitor probing
           guessable slugs is trying to find out. */
        User user = userRepository.findPublicProfileBySlug(cleanSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty profile not found"));

        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        long total = 0L;
        for (Object[] row : achievementRepository.countPublicAchievementsByCategoryForSlug(cleanSlug)) {
            String categoryCode = row[0] != null ? row[0].toString() : null;
            long count = toLongOrZero(row[1]);
            if (categoryCode == null || count == 0L) continue;
            categoryCounts.put(categoryCode, count);
            total += count;
        }

        return PublicFacultyProfileResponse.fromEntity(user, total, categoryCounts);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PublicAchievementResponse> getFacultyAchievements(String slug, String categoryCode,
                                                                           int page, int size) {
        String cleanSlug = blankToNull(slug);
        if (cleanSlug == null) {
            throw new ResourceNotFoundException("Faculty profile not found");
        }

        Pageable pageable = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "achievementDate"));

        Page<Achievement> achievements = achievementRepository.findPublicAchievementsBySlug(
                cleanSlug,
                blankToNull(categoryCode),
                pageable
        );

        return PagedResponse.from(achievements, PublicAchievementResponse::fromEntity);
    }

    /* ================================================================
       Public research gallery
       ================================================================ */

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PublicAchievementResponse> searchAchievements(String keyword, String categoryCode,
                                                                       String departmentCode, int page, int size) {
        Pageable pageable = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "achievementDate"));

        Page<Achievement> achievements = achievementRepository.searchPublicAchievements(
                blankToNull(keyword),
                blankToNull(categoryCode),
                blankToNull(departmentCode),
                pageable
        );

        return PagedResponse.from(achievements, PublicAchievementResponse::fromEntity);
    }

    /* ================================================================
       Departments
       ================================================================ */

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getDepartments() {
        /* The single-argument factory on purpose. The two-argument one fills in
           userCount, and how many staff each department employs is not something
           a public filter dropdown needs to publish. Because userCount is
           annotated NON_NULL it is omitted from the JSON entirely rather than
           appearing as null. */
        return departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(d -> d.getCode() == null ? "" : d.getCode()))
                .map(DepartmentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /* ================================================================
       Helpers
       ================================================================ */

    /**
     * Builds a page request, clamping the caller's numbers into a sane range.
     *
     * <p>A negative page or a zero size would throw straight out of Spring Data
     * as a 500; clamping turns a malformed public request into a sensible first
     * page instead of an error page, and caps {@code size} so the endpoint cannot
     * be used to pull the whole table in one call.
     */
    private Pageable pageRequest(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }

    /**
     * Treats {@code ""} and {@code "   "} as "no filter".
     *
     * <p>Needed because an empty text box submits an empty string, not null, and
     * the repository queries switch on {@code :param IS NULL}. Without this, a
     * cleared search box would filter for records whose title contains an empty
     * string — harmless here, but it also means {@code departmentCode=""} would
     * match no department at all and silently return nothing.
     */
    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private long toLongOrZero(Object value) {
        Long result = toLong(value);
        return result == null ? 0L : result;
    }
}
