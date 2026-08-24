/* ====================================================================
   public-sample-data.js
   ⚠️  SAMPLE CONTENT ONLY — NOT REAL PORTAL DATA. DELETE WITH TRACK B.
   --------------------------------------------------------------------
   READ THIS BEFORE CHANGING ANYTHING ELSE ON THE PUBLIC PAGES.

   The public pages are meant to read live data from the backend:

       GET /api/public/faculty
       GET /api/public/faculty/{slug}
       GET /api/public/faculty/{slug}/achievements
       GET /api/public/achievements
       GET /api/public/departments

   None of those endpoints exist yet. They are Track B work. Right now
   SecurityConfig ends in `anyRequest().authenticated()`, `/api/public/**`
   is not whitelisted, there is no PublicController, and the
   `achievements.visibility` column has not been added (that is migration
   V4). So a visitor with no token cannot read anything from the API.

   Rather than invent a fake API or leave the pages blank, every page:
     1. asks the real endpoint first, via PublicApi.tryGet(...)
     2. falls back to this file when that request fails
     3. shows a visible "sample content" banner while doing so

   THIS IS THE ONLY FILE IN THE PROJECT THAT CONTAINS INVENTED NAMES,
   TITLES OR NUMBERS. Nothing here is a real NIET person, paper, patent
   or grant. When the Track B endpoints go live the banner disappears on
   its own, and this file plus its <script> tags can be deleted outright.

   What IS real, and deliberately so:
     · the six department codes (CSE, IT, ECE, EEE, MECH, CIVIL) match
       the rows seeded by V2__seed_reference_data.sql
     · the five category codes match the seeded achievement_categories
     · every field name and enum value below matches the actual entity
       (Publication, Patent, ResearchGrant, WorkshopFdp, Award), so
       swapping in the real API response is a like-for-like replacement

   Note the deliberately non-public records near the end of the
   achievements array — PRIVATE, UNLISTED, PENDING and REJECTED. They
   are there so the visibility filter in public-common.js is actually
   exercised on screen. If any of them ever shows up on a public page,
   that filter is broken.
   ==================================================================== */

const PublicSampleData = (function () {

  /* ─── Departments (codes match the real seed data) ─── */
  const departments = [
    { code: 'CSE',   name: 'Computer Science & Engineering' },
    { code: 'IT',    name: 'Information Technology' },
    { code: 'ECE',   name: 'Electronics & Communication Engineering' },
    { code: 'EEE',   name: 'Electrical & Electronics Engineering' },
    { code: 'MECH',  name: 'Mechanical Engineering' },
    { code: 'CIVIL', name: 'Civil Engineering' }
  ];

  /* ─── Faculty ─── SAMPLE PEOPLE. NOT REAL STAFF.
     Fields mirror the planned PublicFacultyResponse: no email, no phone,
     no employee id — those are never exposed publicly. */
  const faculty = [
    { slug: 'a-sample-faculty-cse',   fullName: 'Dr. A. Sample',        designation: 'Professor',           departmentCode: 'CSE' },
    { slug: 'b-sample-faculty-cse',   fullName: 'Dr. B. Sample',        designation: 'Associate Professor', departmentCode: 'CSE' },
    { slug: 'c-sample-faculty-cse',   fullName: 'Prof. C. Sample',      designation: 'Assistant Professor', departmentCode: 'CSE' },
    { slug: 'd-sample-faculty-it',    fullName: 'Dr. D. Sample',        designation: 'Professor & Head',    departmentCode: 'IT'  },
    { slug: 'e-sample-faculty-it',    fullName: 'Dr. E. Sample',        designation: 'Associate Professor', departmentCode: 'IT'  },
    { slug: 'f-sample-faculty-ece',   fullName: 'Dr. F. Sample',        designation: 'Professor',           departmentCode: 'ECE' },
    { slug: 'g-sample-faculty-ece',   fullName: 'Prof. G. Sample',      designation: 'Assistant Professor', departmentCode: 'ECE' },
    { slug: 'h-sample-faculty-eee',   fullName: 'Dr. H. Sample',        designation: 'Associate Professor', departmentCode: 'EEE' },
    { slug: 'i-sample-faculty-mech',  fullName: 'Dr. I. Sample',        designation: 'Professor & Head',    departmentCode: 'MECH' },
    { slug: 'j-sample-faculty-mech',  fullName: 'Prof. J. Sample',      designation: 'Assistant Professor', departmentCode: 'MECH' },
    { slug: 'k-sample-faculty-civil', fullName: 'Dr. K. Sample',        designation: 'Professor',           departmentCode: 'CIVIL' },
    { slug: 'l-sample-faculty-civil', fullName: 'Dr. L. Sample',        designation: 'Associate Professor', departmentCode: 'CIVIL' }
  ];

  /* ─── Achievements ─── SAMPLE RECORDS. NOT REAL RESEARCH OUTPUT.
     `status` and `visibility` are included on purpose: the public pages
     must show a record only when status === 'APPROVED' AND
     visibility === 'PUBLIC'. */
  const achievements = [

    /* ---------- PUBLICATION ---------- */
    {
      id: 's1', facultySlug: 'a-sample-faculty-cse',
      categoryCode: 'PUBLICATION', title: 'Sample paper on federated learning for medical image analysis',
      description: 'A sample abstract describing a federated training scheme that keeps patient scans on the originating hospital network while still producing a shared diagnostic model.',
      achievementDate: '2026-03-14', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC', featured: true,
      publication: {
        publicationType: 'JOURNAL', journalConferenceName: 'Sample Journal of Applied Computing',
        publisher: 'Sample Academic Press', doi: '10.0000/sample.2026.0001',
        volume: '18', issue: '2', pages: '221-238', indexing: 'SCOPUS', impactFactor: '3.40'
      }
    },
    {
      id: 's2', facultySlug: 'd-sample-faculty-it',
      categoryCode: 'PUBLICATION', title: 'Sample study of anomaly detection in campus network traffic',
      description: 'A sample abstract on an unsupervised approach to spotting unusual traffic patterns without a labelled attack dataset.',
      achievementDate: '2026-01-22', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC', featured: true,
      publication: {
        publicationType: 'CONFERENCE', journalConferenceName: 'Sample International Conference on Network Security',
        publisher: 'Sample Conference Society', doi: '10.0000/sample.2026.0002',
        pages: '77-84', indexing: 'WEB_OF_SCIENCE'
      }
    },
    {
      id: 's3', facultySlug: 'f-sample-faculty-ece',
      categoryCode: 'PUBLICATION', title: 'Sample work on low-power antenna design for IoT sensors',
      description: 'A sample abstract covering a compact antenna geometry intended for battery-powered environmental sensors.',
      achievementDate: '2025-11-08', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC',
      publication: {
        publicationType: 'JOURNAL', journalConferenceName: 'Sample Review of Electronics & Communication',
        publisher: 'Sample Technical Publishers', doi: '10.0000/sample.2025.0003',
        volume: '9', issue: '4', pages: '512-527', indexing: 'SCOPUS', impactFactor: '2.10'
      }
    },
    {
      id: 's4', facultySlug: 'b-sample-faculty-cse',
      categoryCode: 'PUBLICATION', title: 'Sample chapter on explainable models in academic assessment',
      description: 'A sample abstract on making automated grading decisions reviewable by a human examiner.',
      achievementDate: '2025-09-30', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC',
      publication: {
        publicationType: 'BOOK_CHAPTER', journalConferenceName: 'Sample Handbook of Educational Technology',
        publisher: 'Sample Academic Press', isbnIssn: '978-0-0000-0000-0',
        pages: '145-172', indexing: 'UGC_CARE'
      }
    },
    {
      id: 's5', facultySlug: 'k-sample-faculty-civil',
      categoryCode: 'PUBLICATION', title: 'Sample assessment of recycled aggregate in structural concrete',
      description: 'A sample abstract reporting compressive strength results for concrete mixes using construction-and-demolition waste.',
      achievementDate: '2025-08-19', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC',
      publication: {
        publicationType: 'JOURNAL', journalConferenceName: 'Sample Journal of Sustainable Construction',
        publisher: 'Sample Engineering Press', doi: '10.0000/sample.2025.0005',
        volume: '12', issue: '3', pages: '89-104', indexing: 'SCOPUS', impactFactor: '2.85'
      }
    },
    {
      id: 's6', facultySlug: 'h-sample-faculty-eee',
      categoryCode: 'PUBLICATION', title: 'Sample analysis of rooftop solar integration on a campus microgrid',
      description: 'A sample abstract modelling voltage stability when distributed rooftop generation is added to an existing campus feeder.',
      achievementDate: '2025-07-11', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC',
      publication: {
        publicationType: 'JOURNAL', journalConferenceName: 'Sample Transactions on Power Systems',
        publisher: 'Sample Technical Publishers', doi: '10.0000/sample.2025.0006',
        volume: '7', issue: '2', pages: '310-325', indexing: 'WEB_OF_SCIENCE', impactFactor: '4.05'
      }
    },

    /* ---------- PATENT ---------- */
    {
      id: 's7', facultySlug: 'a-sample-faculty-cse',
      categoryCode: 'PATENT', title: 'Sample patent: adaptive scheduling method for edge compute nodes',
      description: 'A sample summary of a scheduling method that shifts workloads between edge nodes based on measured thermal headroom.',
      achievementDate: '2026-02-05', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC', featured: true,
      patent: { patentNumber: 'SAMPLE-000000001', patentStatus: 'GRANTED', country: 'India', filingDate: '2024-06-18', grantDate: '2026-02-05' }
    },
    {
      id: 's8', facultySlug: 'i-sample-faculty-mech',
      categoryCode: 'PATENT', title: 'Sample patent: vibration damping mount for portable machinery',
      description: 'A sample summary of a layered mount intended to reduce transmitted vibration in hand-positioned equipment.',
      achievementDate: '2025-12-01', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC',
      patent: { patentNumber: 'SAMPLE-000000002', patentStatus: 'PUBLISHED', country: 'India', filingDate: '2025-01-09' }
    },
    {
      id: 's9', facultySlug: 'g-sample-faculty-ece',
      categoryCode: 'PATENT', title: 'Sample patent: self-calibrating sensor array for air quality monitoring',
      description: 'A sample summary of an array that periodically re-baselines individual sensors against a shared reference reading.',
      achievementDate: '2025-10-16', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC',
      patent: { patentNumber: 'SAMPLE-000000003', patentStatus: 'FILED', country: 'India', filingDate: '2025-10-16' }
    },

    /* ---------- RESEARCH_GRANT ---------- */
    {
      id: 's10', facultySlug: 'd-sample-faculty-it',
      categoryCode: 'RESEARCH_GRANT', title: 'Sample funded project on privacy-preserving analytics',
      description: 'A sample summary of a funded project studying how aggregate reporting can be produced without exposing individual records.',
      achievementDate: '2025-06-02', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC', featured: true,
      researchGrant: {
        fundingAgency: 'Sample National Research Agency', projectTitle: 'Privacy-preserving analytics for institutional reporting',
        grantAmount: '2850000', projectType: 'RESEARCH', durationMonths: 36, grantStatus: 'ONGOING'
      }
    },
    {
      id: 's11', facultySlug: 'k-sample-faculty-civil',
      categoryCode: 'RESEARCH_GRANT', title: 'Sample consultancy on structural health monitoring',
      description: 'A sample summary of a consultancy engagement instrumenting an existing structure with strain and tilt sensors.',
      achievementDate: '2025-04-21', academicYear: '2024-25',
      status: 'APPROVED', visibility: 'PUBLIC',
      researchGrant: {
        fundingAgency: 'Sample State Infrastructure Board', projectTitle: 'Structural health monitoring pilot',
        grantAmount: '1240000', projectType: 'CONSULTANCY', durationMonths: 18, grantStatus: 'SANCTIONED'
      }
    },
    {
      id: 's12', facultySlug: 'f-sample-faculty-ece',
      categoryCode: 'RESEARCH_GRANT', title: 'Sample grant for a shared RF measurement facility',
      description: 'A sample summary of an equipment grant establishing a shared radio-frequency measurement setup for student projects.',
      achievementDate: '2025-02-14', academicYear: '2024-25',
      status: 'APPROVED', visibility: 'PUBLIC',
      researchGrant: {
        fundingAgency: 'Sample Technical Education Council', projectTitle: 'Shared RF measurement facility',
        grantAmount: '4600000', projectType: 'INFRASTRUCTURE', durationMonths: 24, grantStatus: 'COMPLETED'
      }
    },

    /* ---------- AWARD ---------- */
    {
      id: 's13', facultySlug: 'b-sample-faculty-cse',
      categoryCode: 'AWARD', title: 'Sample award for excellence in teaching',
      description: 'A sample citation recognising sustained classroom innovation and student mentoring.',
      achievementDate: '2026-04-09', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC', featured: true,
      award: { awardName: 'Sample Excellence in Teaching Award', awardingBody: 'Sample Academic Council', awardLevel: 'NATIONAL' }
    },
    {
      id: 's14', facultySlug: 'i-sample-faculty-mech',
      categoryCode: 'AWARD', title: 'Sample best paper award at an international conference',
      description: 'A sample citation for the highest-rated submission in its track.',
      achievementDate: '2025-11-27', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC',
      award: { awardName: 'Sample Best Paper Award', awardingBody: 'Sample Conference Society', awardLevel: 'INTERNATIONAL' }
    },
    {
      id: 's15', facultySlug: 'l-sample-faculty-civil',
      categoryCode: 'AWARD', title: 'Sample state-level recognition for sustainable design',
      description: 'A sample citation for a low-embodied-carbon design submission.',
      achievementDate: '2025-05-30', academicYear: '2024-25',
      status: 'APPROVED', visibility: 'PUBLIC',
      award: { awardName: 'Sample Sustainable Design Recognition', awardingBody: 'Sample State Engineering Board', awardLevel: 'STATE' }
    },

    /* ---------- WORKSHOP_FDP ---------- */
    {
      id: 's16', facultySlug: 'c-sample-faculty-cse',
      categoryCode: 'WORKSHOP_FDP', title: 'Sample faculty development programme on modern curriculum design',
      description: 'A sample summary of a two-week programme on outcome-based course planning.',
      achievementDate: '2026-01-12', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC',
      workshopFdp: {
        eventName: 'Sample FDP on Outcome-Based Curriculum Design', eventType: 'FDP',
        role: 'RESOURCE_PERSON', location: 'Greater Noida', durationDays: 14, organizingBody: 'Sample Teaching Academy'
      }
    },
    {
      id: 's17', facultySlug: 'e-sample-faculty-it',
      categoryCode: 'WORKSHOP_FDP', title: 'Sample certification in cloud infrastructure',
      description: 'A sample summary of a professional certification covering deployment and cost management on managed cloud platforms.',
      achievementDate: '2025-09-05', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC',
      workshopFdp: {
        eventName: 'Sample Cloud Infrastructure Certification', eventType: 'CERTIFICATION',
        role: 'ATTENDED', location: 'Online', durationDays: 5, organizingBody: 'Sample Cloud Institute'
      }
    },
    {
      id: 's18', facultySlug: 'j-sample-faculty-mech',
      categoryCode: 'WORKSHOP_FDP', title: 'Sample workshop on additive manufacturing for teaching labs',
      description: 'A sample summary of a hands-on workshop on integrating 3D printing into undergraduate laboratory work.',
      achievementDate: '2025-07-24', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PUBLIC',
      workshopFdp: {
        eventName: 'Sample Workshop on Additive Manufacturing', eventType: 'WORKSHOP',
        role: 'ORGANIZED', location: 'Greater Noida', durationDays: 3, organizingBody: 'Sample Manufacturing Forum'
      }
    },

    /* ================================================================
       DELIBERATELY NOT PUBLIC — these four must NEVER render on a
       public page. They exist to prove the visibility filter works.
       If you see any of them on screen, isPubliclyVisible() is broken.
       ================================================================ */
    {
      id: 's19', facultySlug: 'a-sample-faculty-cse',
      categoryCode: 'PUBLICATION', title: 'MUST NOT APPEAR — private draft manuscript',
      description: 'Approved by a reviewer but marked PRIVATE by its owner. Private means private.',
      achievementDate: '2026-05-01', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'PRIVATE',
      publication: { publicationType: 'JOURNAL', journalConferenceName: 'Should never be shown', indexing: 'OTHER' }
    },
    {
      id: 's20', facultySlug: 'd-sample-faculty-it',
      categoryCode: 'PUBLICATION', title: 'MUST NOT APPEAR — unlisted preprint',
      description: 'UNLISTED records are reachable only through a share link, never through public search or the gallery.',
      achievementDate: '2026-05-02', academicYear: '2025-26',
      status: 'APPROVED', visibility: 'UNLISTED',
      publication: { publicationType: 'JOURNAL', journalConferenceName: 'Should never be shown', indexing: 'OTHER' }
    },
    {
      id: 's21', facultySlug: 'b-sample-faculty-cse',
      categoryCode: 'PATENT', title: 'MUST NOT APPEAR — pending verification',
      description: 'Marked PUBLIC by its owner but still awaiting review. Unverified claims are not published.',
      achievementDate: '2026-05-03', academicYear: '2025-26',
      status: 'PENDING', visibility: 'PUBLIC',
      patent: { patentNumber: 'SHOULD-NOT-SHOW', patentStatus: 'FILED', country: 'India' }
    },
    {
      id: 's22', facultySlug: 'c-sample-faculty-cse',
      categoryCode: 'AWARD', title: 'MUST NOT APPEAR — rejected record',
      description: 'Rejected during review. Neither the record nor the reviewer comment is ever public.',
      achievementDate: '2026-05-04', academicYear: '2025-26',
      status: 'REJECTED', visibility: 'PUBLIC',
      award: { awardName: 'Should never be shown', awardingBody: 'Should never be shown', awardLevel: 'INSTITUTIONAL' }
    }
  ];

  /* ─── Derived helpers ───
     Counts are computed, never typed in, so the numbers on screen can
     never contradict the list they came from. */

  function departmentName(code) {
    const match = departments.filter(function (d) { return d.code === code; })[0];
    return match ? match.name : code;
  }

  /** Only APPROVED + PUBLIC records — the same rule the backend will enforce. */
  function publicAchievements() {
    return achievements.filter(function (a) {
      return a.status === 'APPROVED' && a.visibility === 'PUBLIC';
    });
  }

  /** Faculty list with a public-achievement count attached to each row. */
  function facultyWithCounts() {
    const visible = publicAchievements();
    return faculty.map(function (person) {
      const mine = visible.filter(function (a) { return a.facultySlug === person.slug; });
      return {
        slug: person.slug,
        fullName: person.fullName,
        designation: person.designation,
        departmentCode: person.departmentCode,
        departmentName: departmentName(person.departmentCode),
        publicAchievementCount: mine.length,
        publicationCount: mine.filter(function (a) { return a.categoryCode === 'PUBLICATION'; }).length
      };
    });
  }

  function facultyBySlug(slug) {
    return facultyWithCounts().filter(function (p) { return p.slug === slug; })[0] || null;
  }

  /** Institution-wide counters for the home page strip. */
  function stats() {
    const visible = publicAchievements();
    const contributing = {};
    visible.forEach(function (a) { contributing[a.facultySlug] = true; });
    return {
      facultyCount: Object.keys(contributing).length,
      achievementCount: visible.length,
      publicationCount: visible.filter(function (a) { return a.categoryCode === 'PUBLICATION'; }).length,
      patentCount: visible.filter(function (a) { return a.categoryCode === 'PATENT'; }).length,
      departmentCount: departments.length
    };
  }

  /** Per-category totals for the "what the portal tracks" pills. */
  function categoryCounts() {
    const visible = publicAchievements();
    const counts = {};
    visible.forEach(function (a) {
      counts[a.categoryCode] = (counts[a.categoryCode] || 0) + 1;
    });
    return counts;
  }

  return {
    isSample: true,
    departments: departments,
    rawAchievements: achievements,
    publicAchievements: publicAchievements,
    facultyWithCounts: facultyWithCounts,
    facultyBySlug: facultyBySlug,
    departmentName: departmentName,
    stats: stats,
    categoryCounts: categoryCounts
  };

})();
