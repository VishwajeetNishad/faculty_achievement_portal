package com.niet.facultyachievement.security;

import com.niet.facultyachievement.controller.ReportController;
import com.niet.facultyachievement.dto.report.NaacCountRow;
import com.niet.facultyachievement.dto.report.NaacReportCoverage;
import com.niet.facultyachievement.dto.report.NaacReportResponse;
import com.niet.facultyachievement.dto.report.NaacSectionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.util.SimpleMethodInvocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may read and who may download the institution-wide accreditation report.
 *
 * <p><strong>How this proves anything without a running server.</strong> The rest
 * of this suite is pure Mockito with no Spring context and no MySQL, and that is
 * kept here. But the rule under test lives in a {@code @PreAuthorize} SpEL string
 * on {@link ReportController}, so asserting the string's text would only prove
 * somebody typed something. Instead these tests hand the real annotation to
 * Spring Security's own {@link DefaultMethodSecurityExpressionHandler} and
 * evaluate it against a real {@link Authentication} — the same evaluator the
 * running application uses, driven directly rather than through HTTP. If the
 * annotation is deleted, weakened, or has its {@code and} turned into an
 * {@code or}, these tests fail.
 *
 * <p>What they deliberately do not cover: the HTTP status code an anonymous
 * request receives. That is decided by the filter chain
 * ({@code .anyRequest().authenticated()} in {@code SecurityConfig}) and comes out
 * as 401 rather than 403. The test below asserts the part that belongs to this
 * feature — that an unauthenticated principal is denied by the expression too, so
 * the endpoint does not depend on the filter chain alone.
 */
@DisplayName("Accreditation report — authorization on /api/reports/naac")
class NaacReportSecurityTest {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_HOD = "ROLE_HOD";
    private static final String ROLE_FACULTY = "ROLE_FACULTY";

    private static Method readMethod() throws Exception {
        return ReportController.class.getMethod("getNaacReport",
                Authentication.class, String.class, String.class, Long.class);
    }

    private static Method exportMethod() throws Exception {
        return ReportController.class.getMethod("exportNaacReportCsv",
                Authentication.class, String.class, String.class, Long.class);
    }

    // ------------------------------------------------------------------
    // The annotation must exist at all
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Every endpoint on the controller carries a @PreAuthorize — none is left open")
    void everyEndpointIsGuarded() {
        List<String> unguarded = new ArrayList<>();
        for (Method method : ReportController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            if (method.getAnnotation(PreAuthorize.class) == null) {
                unguarded.add(method.getName());
            }
        }
        assertTrue(unguarded.isEmpty(),
                "These report endpoints have no @PreAuthorize and would fall back to "
                        + "'any authenticated user', which includes every faculty member: " + unguarded);
    }

    // ------------------------------------------------------------------
    // Reading the report
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Read: a faculty member is denied — the report covers the whole institution")
    void facultyCannotReadReport() throws Exception {
        assertFalse(allows(readMethod(), authenticated(ROLE_FACULTY)),
                "A faculty member must not be able to read every department's records");
    }

    @Test
    @DisplayName("Read: a Head of Department without VIEW_REPORTS is denied")
    void hodWithoutPermissionCannotReadReport() throws Exception {
        assertFalse(allows(readMethod(), authenticated(ROLE_HOD)),
                "A HOD sees their own department through the HOD portal; the institution-wide "
                        + "report needs VIEW_REPORTS to be granted explicitly");
    }

    @Test
    @DisplayName("Read: an administrator is allowed, with no permission grant needed")
    void adminCanReadReport() throws Exception {
        assertTrue(allows(readMethod(), authenticated(ROLE_ADMIN)));
    }

    @Test
    @DisplayName("Read: a non-admin granted VIEW_REPORTS is allowed — this is what the permission is for")
    void nonAdminWithViewReportsCanReadReport() throws Exception {
        assertTrue(allows(readMethod(), authenticated(ROLE_HOD, Permissions.VIEW_REPORTS)),
                "VIEW_REPORTS exists so a HOD preparing the accreditation file can be given "
                        + "institution-wide read access without being made an administrator");
    }

    @Test
    @DisplayName("Read: an unauthenticated principal is denied by the expression, not only by the filter chain")
    void anonymousCannotReadReport() throws Exception {
        assertFalse(allows(readMethod(), anonymous()));
    }

    // ------------------------------------------------------------------
    // Downloading the report — the least-privilege decision
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Export: VIEW_REPORTS alone is not enough to download")
    void viewReportsAloneCannotExport() throws Exception {
        assertFalse(allows(exportMethod(), authenticated(ROLE_HOD, Permissions.VIEW_REPORTS)),
                "Reading on screen and taking a copy away are separate grants");
    }

    @Test
    @DisplayName("Export: EXPORT_REPORTS without VIEW_REPORTS is denied")
    void exportReportsWithoutViewIsDenied() throws Exception {
        // The permissions screen grants the two independently, so this is a state
        // an administrator can create by accident. Being able to download a file
        // you are not allowed to open on screen is the wrong answer to it.
        assertFalse(allows(exportMethod(), authenticated(ROLE_HOD, Permissions.EXPORT_REPORTS)),
                "Downloading data the account may not view on screen must be refused");
    }

    @Test
    @DisplayName("Export: both permissions together are allowed")
    void bothPermissionsCanExport() throws Exception {
        assertTrue(allows(exportMethod(),
                authenticated(ROLE_HOD, Permissions.VIEW_REPORTS, Permissions.EXPORT_REPORTS)));
    }

    @Test
    @DisplayName("Export: an administrator is allowed, so nothing an admin could do has been taken away")
    void adminCanExport() throws Exception {
        assertTrue(allows(exportMethod(), authenticated(ROLE_ADMIN)));
    }

    @Test
    @DisplayName("Export: a faculty member is denied")
    void facultyCannotExport() throws Exception {
        assertFalse(allows(exportMethod(), authenticated(ROLE_FACULTY)));
    }

    // ------------------------------------------------------------------
    // What the payload is structurally incapable of carrying
    // ------------------------------------------------------------------

    /**
     * The report is admin-scoped, so unlike the public DTOs it may legitimately
     * carry names and employee ids. What it must never carry is a credential or a
     * secret — a field that does not exist on the class can never be serialised,
     * however the service is later changed.
     */
    @Test
    @DisplayName("No report DTO declares a credential or secret field")
    void reportDtosCarryNoCredentials() {
        List<String> forbidden = List.of("password", "passwordhash", "hash", "secret",
                "token", "jwt", "credential");

        Class<?>[] dtos = {NaacReportResponse.class, NaacSectionResponse.class,
                NaacCountRow.class, NaacReportCoverage.class};

        List<String> violations = new ArrayList<>();
        for (Class<?> dto : dtos) {
            for (Field field : dto.getDeclaredFields()) {
                if (field.isSynthetic()) {
                    continue; // e.g. $jacocoData added by coverage instrumentation
                }
                String name = field.getName().toLowerCase(Locale.ROOT);
                for (String bad : forbidden) {
                    if (name.contains(bad)) {
                        violations.add(dto.getSimpleName() + "." + field.getName());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Report DTOs must not declare credential or secret fields, but found: " + violations);
    }

    // ------------------------------------------------------------------
    // Evaluating the real annotation with Spring Security's own evaluator
    // ------------------------------------------------------------------

    private boolean allows(Method method, Authentication authentication) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, method.getName() + " has no @PreAuthorize");

        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();

        // Argument slots are filled with nulls only so the invocation's shape
        // matches the method's. The expressions under test reference no parameter.
        Object[] arguments = new Object[method.getParameterCount()];

        EvaluationContext context = handler.createEvaluationContext(authentication,
                new SimpleMethodInvocation(new Object(), method, arguments));

        Boolean result = handler.getExpressionParser()
                .parseExpression(annotation.value())
                .getValue(context, Boolean.class);

        return Boolean.TRUE.equals(result);
    }

    /**
     * @param role       the Spring role authority, e.g. {@code ROLE_HOD}
     * @param permissions individual permission codes, granted as plain authorities
     *                    exactly as {@code UserPermissionResolver} grants them
     */
    private Authentication authenticated(String role, String... permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role));
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        return new UsernamePasswordAuthenticationToken("someone@niet.co.in", "n/a", authorities);
    }

    private Authentication anonymous() {
        return new AnonymousAuthenticationToken("key", "anonymousUser",
                Arrays.asList(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    }
}
