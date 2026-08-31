-- ====================================================================
-- V5: Homepage highlight banners (the auto-sliding carousel)
--
-- WHY THIS EXISTS
-- The public home page needs a rotating banner of institutional posters
-- — an award, a lab inauguration, a conference appearance. Those are
-- marketing creatives, produced by the college's communications team,
-- and they change every few weeks.
--
-- They are NOT achievement records, and this table is deliberately not
-- part of the `achievements` tree:
--
--   achievements       -> a verified claim by one faculty member, with a
--                         proof document, an approving HOD and an audit
--                         trail. Counted in every statistic on the site.
--   homepage_highlights -> a picture the institution chose to show on its
--                         front page. Counted in nothing.
--
-- Mixing them would corrupt the numbers. If an award poster were filed
-- as an award record, the public "Awards" figure would rise without any
-- faculty member having submitted anything, and the portal's central
-- promise — that every number on the public site traces back to a
-- verified submission — would quietly stop being true.
--
-- WHY A TABLE INSTEAD OF FILES IN THE REPOSITORY
-- The alternative was dropping the images into `frontend/assets/` and
-- listing them in a JSON file. That is less code, but every future
-- poster change would then need a developer, a commit and a redeploy.
-- With this table the college's own staff replace the banners from the
-- admin screen, indefinitely, with no code change.
--
-- WHAT IS STORED WHERE
-- This table holds METADATA ONLY. The image bytes live on disk, in the
-- highlights upload directory (`app.highlight-storage.upload-dir`),
-- exactly like achievement proof PDFs. Putting multi-megabyte binaries
-- in MySQL bloats every backup and every replica for no benefit.
--
-- The table is created EMPTY. Nothing appears on the home page until an
-- administrator uploads something — the same "nothing is published by
-- surprise" stance as V4.
--
-- This migration does NOT touch achievements, users, roles, or any
-- existing row's data.
-- ====================================================================


-- ====================================================================
-- 1. The highlight banners
-- ====================================================================
CREATE TABLE IF NOT EXISTS `homepage_highlights` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- The stored file is a UUID inside the highlights upload directory.
    -- The name the browser submitted is never used and never trusted: it
    -- is attacker-controlled and is the classic path-traversal vector
    -- ("../../etc/passwd"). 120 chars is ample for a UUID plus extension.
    `stored_filename`  VARCHAR(120) NOT NULL,

    -- The format the server DETECTED from the file's magic bytes, not the
    -- Content-Type the browser claimed. Sent back on the image response so
    -- the browser renders it correctly.
    `content_type`     VARCHAR(40)  NOT NULL,   -- image/png | image/jpeg | image/webp

    `file_size_bytes`  BIGINT       NOT NULL,

    -- Read from the image header at upload time. Two uses: the <img> tag
    -- carries width/height so the browser reserves space before the image
    -- arrives (no layout shift), and the admin screen can warn when a
    -- poster is too small for the banner frame and will look soft.
    `image_width`      INT          NOT NULL,
    `image_height`     INT          NOT NULL,

    -- Required, not optional. A homepage banner with no alt text is
    -- invisible to a screen reader, and this doubles as the row's label in
    -- the admin list, so there is no such thing as a nameless highlight.
    `alt_text` VARCHAR(255) NOT NULL,

    -- Optional caption shown under the slide. These posters already carry
    -- all their own text, so leaving it blank is the normal case rather
    -- than a missing value.
    `caption`  VARCHAR(160) NULL,

    -- Optional click-through. Validated in the service layer: only http,
    -- https or a site-relative path is accepted, because a `javascript:`
    -- URL here would become a clickable script on the front page.
    `link_url` VARCHAR(500) NULL,

    -- Which part of the image survives the CSS `object-fit: cover` crop.
    --
    -- Stored as a fixed ENUM and NEVER as free-text CSS. An admin-supplied
    -- string flowing into an `object-position` value would be a CSS
    -- injection surface; the front end maps this name to a stylesheet
    -- class instead, so no uploaded text ever becomes a style value.
    `focal_point` ENUM('TOP_LEFT','TOP_CENTER','TOP_RIGHT',
                       'CENTER_LEFT','CENTER','CENTER_RIGHT',
                       'BOTTOM_LEFT','BOTTOM_CENTER','BOTTOM_RIGHT')
                  NOT NULL DEFAULT 'CENTER',

    `display_order` INT     NOT NULL DEFAULT 0,

    -- Lets an administrator retire a poster without destroying it, so last
    -- year's banners can come back next year. An inactive highlight is not
    -- listed AND its image stops being downloadable — see
    -- HighlightServiceImpl.loadImage.
    `active`        BOOLEAN NOT NULL DEFAULT TRUE,

    `uploaded_by_user_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Bumped by Hibernate on every change. The public image URL carries
    -- this as a `?v=` token, which is what makes a one-year cache header
    -- safe: replacing an image changes the URL, so every browser refetches.
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- RESTRICT, not CASCADE. Deleting a staff account must never silently
    -- blank the institution's home page; the highlights have to be
    -- reassigned or removed deliberately first.
    CONSTRAINT `fk_highlights_uploader`
        FOREIGN KEY (`uploaded_by_user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,

    -- The only query the public endpoint ever runs is
    -- "active slides, in display order".
    INDEX `idx_highlights_active_order` (`active`, `display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ====================================================================
-- 2. The permission that guards the admin screen
--
-- Added to the same catalogue V3 created. Permissions.ALL in Java must
-- match this table exactly or PermissionCatalogValidator logs an error at
-- startup, so the constant and this row are always changed together.
--
-- MANAGE_HIGHLIGHTS is intentionally NOT in ADMIN_ONLY_GRANTABLE: it
-- edits marketing content and cannot hand out any further power, so an
-- Admin may delegate it to a communications staff member the same way
-- MANAGE_DEPARTMENTS is delegated.
-- ====================================================================
INSERT INTO `permissions` (`id`, `permission_code`, `description`) VALUES
(16, 'MANAGE_HIGHLIGHTS', 'Upload, reorder and remove the homepage highlight banners')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);
