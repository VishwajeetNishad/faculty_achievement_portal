-- ====================================================================
-- V4: Achievement visibility + temporary share links (Track B)
--
-- WHY THIS EXISTS
-- Until now an achievement had exactly one flag that mattered: `status`
-- (PENDING / APPROVED / REJECTED). That answers "has the department
-- checked this record?" It does NOT answer "may a stranger on the
-- internet read it?" Those are two different questions, and the portal
-- now needs both answers:
--
--   status     -> has it been verified?      (unchanged, still owned by HOD/Admin)
--   visibility -> who is allowed to see it?  (new, owned by the faculty member)
--
-- The two are deliberately kept independent. A record becomes publicly
-- readable only when BOTH are true:
--
--     status = 'APPROVED'  AND  visibility = 'PUBLIC'
--
-- Anything else stays invisible to the public. That rule is enforced in
-- the service layer, not by the caller — the public API never accepts
-- status or visibility as a filter it can be talked out of.
--
-- SAFETY: the new column defaults to 'PRIVATE'. Every achievement that
-- already exists in this database therefore becomes PRIVATE the moment
-- this migration runs. Nothing is published by surprise; a faculty
-- member has to choose PUBLIC themselves.
--
-- This migration does NOT touch `status`, does NOT touch any existing
-- row's data, and does NOT change a single permission or role.
-- ====================================================================


-- ====================================================================
-- 1. Achievement visibility and keywords
-- ====================================================================

-- WHY AN ENUM AND NOT A BOOLEAN
-- "Public or not" would need two columns to express the third case, and
-- the third case is the interesting one:
--
--   PUBLIC   - listed in the public directory, gallery and search
--   UNLISTED - reachable ONLY through a share link the owner generates.
--              Never listed, never searchable, never in the gallery.
--              This is how unpublished research is shown to a reviewer
--              or a collaborator who has no account.
--   PRIVATE  - visible to the owner, their HOD and Admins. Nobody else.
--
-- NOT NULL + DEFAULT 'PRIVATE' means old rows and any future INSERT that
-- forgets the column both land on the safest possible value.
ALTER TABLE `achievements`
    ADD COLUMN `visibility` ENUM('PUBLIC', 'UNLISTED', 'PRIVATE')
        NOT NULL DEFAULT 'PRIVATE' AFTER `status`;

-- WHY KEYWORDS
-- The public gallery needs to be searchable by subject, and the existing
-- columns are not enough: `title` is a single sentence and `description`
-- is prose. Keywords are the author's own comma-separated terms
-- ("federated learning, edge computing"), which is what a student
-- actually types into a search box.
--
-- Kept as one VARCHAR rather than a separate keywords table on purpose.
-- A tag table would be the textbook answer, but nothing in this portal
-- needs to list "all achievements sharing tag X" as a first-class
-- feature, and one column is far less machinery to get wrong.
ALTER TABLE `achievements`
    ADD COLUMN `keywords` VARCHAR(500) NULL AFTER `description`;

-- Index on visibility alone, for "show me everything unlisted" style
-- queries from the owner's own dashboard.
CREATE INDEX `idx_achievements_visibility` ON `achievements` (`visibility`);

-- The composite index is the important one. EVERY public query filters on
-- status AND visibility together, so a single index covering both lets
-- MySQL find public records without scanning the table. Column order
-- matters: status first because it is the more selective of the two.
CREATE INDEX `idx_achievements_public` ON `achievements` (`status`, `visibility`);


-- ====================================================================
-- 2. Readable public profile addresses
-- ====================================================================

-- WHY NOT JUST USE THE DATABASE ID
-- A public profile could live at /faculty/17, but that is worse in three
-- ways: it is ugly to share, it tells the world how many users exist and
-- in what order they were created, and it invites visitors to try
-- /faculty/18 to see who else is in the system.
--
-- A slug ("rajesh-kumar-cse") reads well, means something to a human,
-- and leaks nothing about the internal numbering.
--
-- NULL is allowed because this column is filled in by
-- PublicSlugBackfill on the next startup rather than by SQL string
-- surgery here. Generating a slug means stripping accents, lowercasing,
-- collapsing punctuation and resolving collisions with a numeric
-- suffix — all of which is straightforward and testable in Java, and
-- all of which is painful and fragile in a MySQL statement.
--
-- UNIQUE is enforced by the database, not just by application code, so
-- two people can never end up sharing a public address even if two
-- requests race each other.
ALTER TABLE `users`
    ADD COLUMN `public_slug` VARCHAR(120) NULL UNIQUE AFTER `email`;


-- ====================================================================
-- 3. Share links for unlisted research
-- ====================================================================

-- WHY A SEPARATE TABLE INSTEAD OF COLUMNS ON `achievements`
-- Putting share_token / expires_at / revoked straight onto the
-- achievements table was considered and rejected. A share link has its
-- own lifecycle: it is created, extended, revoked and replaced, all
-- while the achievement itself sits unchanged. Mixing the two would mean
-- every achievement row carries four columns that are NULL for the
-- majority of records, and replacing a link would destroy the history of
-- the previous one.
--
-- A share link is a BEARER CREDENTIAL. Whoever holds the token can read
-- the record — there is no account, no password and no second check.
-- That is the whole point (a reviewer with no login can open it), and it
-- is also the risk, which is why:
--   * the token is 32 random bytes from SecureRandom, never derived from
--     an id, an employee number or a timestamp
--   * expiry is checked by the server on EVERY request, never by the
--     browser
--   * revoked is a hard stop, independent of expiry
--   * the proof PDF is only reachable when the owner explicitly opted in
CREATE TABLE IF NOT EXISTS `share_links` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `achievement_id` BIGINT NOT NULL,
    `created_by_user_id` BIGINT NOT NULL,

    -- The secret itself. 32 random bytes in URL-safe base64 without
    -- padding is 43 characters; 64 leaves room to lengthen it later
    -- without another migration.
    `share_token` VARCHAR(64) NOT NULL UNIQUE,

    -- NULL means "no expiry" — a permanent link. The UI warns about this
    -- because a permanent link to unpublished research is a standing
    -- credential, but the feature is required, and revoking is one click.
    `expires_at` DATETIME NULL,

    -- Off by default. The proof document is a PDF the faculty member
    -- uploaded as evidence; sharing the record does not automatically
    -- mean sharing the file.
    `include_proof_document` BOOLEAN NOT NULL DEFAULT FALSE,

    -- Revocation is kept as a flag rather than deleting the row, so that
    -- a visitor who tries a killed link can be told "this link was
    -- revoked" instead of the misleading "no such link".
    `revoked` BOOLEAN NOT NULL DEFAULT FALSE,
    `revoked_at` DATETIME NULL,

    -- Purely informational: how many times the link has been opened and
    -- when it was last opened, so the owner can see whether the person
    -- they sent it to actually looked. Deliberately NOT a per-visitor
    -- log — no IP addresses, no user agents, nothing that would turn a
    -- share link into a tracking device.
    `access_count` BIGINT NOT NULL DEFAULT 0,
    `last_accessed_at` DATETIME NULL,

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Deleting the achievement must kill its links; a link to a deleted
    -- record would otherwise keep resolving to nothing.
    CONSTRAINT `fk_share_links_achievement`
        FOREIGN KEY (`achievement_id`) REFERENCES `achievements` (`id`) ON DELETE CASCADE,
    -- Deleting the creator kills their links too. A share link with no
    -- accountable owner should not survive.
    CONSTRAINT `fk_share_links_creator`
        FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,

    -- The token lookup is the hottest query on this table: every public
    -- visit is "find the row with this token".
    INDEX `idx_share_links_token` (`share_token`),
    INDEX `idx_share_links_achievement` (`achievement_id`),
    INDEX `idx_share_links_creator` (`created_by_user_id`),
    INDEX `idx_share_links_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
