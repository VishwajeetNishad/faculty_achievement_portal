package com.niet.facultyachievement.entity;

/**
 * Who is allowed to see an achievement.
 *
 * <p>This is a completely separate question from {@link AchievementStatus}.
 * Status says whether the department has verified the record; visibility says
 * who may read it. A record is only readable by the public when
 * <strong>both</strong> are satisfied:
 *
 * <pre>
 *     status == APPROVED  &amp;&amp;  visibility == PUBLIC
 * </pre>
 *
 * <p>Keeping them independent matters. A faculty member can mark work PUBLIC
 * while it is still pending, and it simply will not appear anywhere until the
 * HOD approves it — no scramble to change a setting after verification. And
 * verification never publishes anything on the owner's behalf.
 */
public enum AchievementVisibility {

    /**
     * Listed in the public directory, the public gallery and public search —
     * but only once the record is also APPROVED.
     */
    PUBLIC,

    /**
     * Reachable only through a share link the owner generates. Never listed,
     * never searchable, never counted in any public total. This is how
     * unpublished work is shown to a reviewer or collaborator who has no
     * account on the portal.
     */
    UNLISTED,

    /**
     * Visible to the owner, their HOD and administrators. Nobody else. This is
     * the default for every new record and for every record that existed
     * before visibility was introduced.
     */
    PRIVATE
}
