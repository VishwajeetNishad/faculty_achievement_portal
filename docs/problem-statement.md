# Problem Statement & Measurable Objectives

**Institution**: Noida Institute of Engineering and Technology (NIET)  

---

## 1. Problem Statement

In many academic institutions, the tracking of faculty achievements—such as journal publications, patents, funded research projects, faculty development programs (FDPs), and national/international awards—suffers from severe operational bottlenecks:

1. **Fragmented & Manual Record Keeping**: Achievements are submitted via emails, physical paper forms, or scattered spreadsheet files. This leads to duplicate records, missing documentation, and data inconsistency.
2. **Loss & Unsecure Storage of Proof Certificates**: Physical certificates or PDF proofs are often mislaid or stored insecurely, rendering verification difficult during institutional audits.
3. **Delayed & Opaque Verification Workflows**: Heads of Department (HODs) lack a unified interface to review, approve, or reject achievement submissions with feedback, leading to verification backlogs.
4. **Labor-Intensive Institutional Reporting**: Generating aggregated reports for national accreditation frameworks (such as NAAC, NBA, NIRF, and NIRF Ranking) requires manual data compilation from multiple departments, wasting hundreds of staff hours annually.
5. **Security & Privacy Deficits**: Unsecured systems risk unauthorized modification of academic records, privilege escalation, and lack of accountability regarding who modified or approved a record.

---

## 2. Measurable Objectives

The **Faculty Achievement Portal** was designed and implemented to meet the following measurable objectives:

1. **Centralized Digital Repository**: Provide a single, unified database to store and manage all NIET faculty achievements across 5 specialized categories.
2. **Digital Verification Workflow**: Streamline approval workflows by giving HODs and Admins structured tools to review, approve, or reject submissions with mandatory comments.
3. **Secure Proof Management**: Enforce strict PDF file validation (magic byte header inspection, 10 MB limit, UUID filenames) to store certificates securely and prevent unauthorized access.
4. **Role-Based Security & IDOR Defense**: Implement robust Spring Security and JWT authentication ensuring Faculty members can only manage their own records, HODs can inspect their department, and Admins oversee the institution.
5. **Real-Time Institutional Analytics**: Automatically calculate and display department comparisons, category breakdowns, and approval status distributions on interactive dashboards.
6. **Authorization-Scoped CSV Export**: Enable instant generation of filtered, UTF-8 BOM CSV reports for accreditation compliance without exposing sensitive security credentials.
7. **Automated Event Notifications**: Trigger instant in-app alerts when achievements are submitted, pending review, approved, or rejected with feedback.
8. **Immutable Security Audit Logging**: Maintain an append-only audit trail recording user logins, achievement lifecycle events, file uploads, and profile updates.
