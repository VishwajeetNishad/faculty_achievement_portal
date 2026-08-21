# Academic Abstract

**Title**: Faculty Achievement Portal — Centralized Digital Tracking, Verification, and Institutional Reporting System  
**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Authors/Developers**: Department of Computer Science & Engineering  

---

## Abstract

Higher education institutions face significant administrative challenges in managing, verifying, and reporting faculty academic contributions across diverse domains including research publications, patents, research grants, workshops/FDPs, and awards. Traditional paper-based or file-folder tracking mechanisms lead to data fragmentation, loss of proof certificates, redundant submissions, delayed verification workflows, and inefficient institutional reporting for accreditation bodies (e.g., NAAC, NBA, NIRF).

This project presents the **Faculty Achievement Portal**, a secure, role-based web application developed specifically for **Noida Institute of Engineering and Technology (NIET)**. The system implements a three-tier architecture leveraging Java 21, Spring Boot 3.3.4, Spring Security, Spring Data JPA, MySQL 8.0, and Vanilla JavaScript.

Key architectural highlights include:
1. **Stateless JWT Authentication & Security**: Enforces role-based access control (FACULTY, HOD, ADMIN) with BCrypt password hashing, token expiration, and complete Insecure Direct Object Reference (IDOR) protection.
2. **Multi-Category Digital Management**: Captures specialized metadata for Publications (DOI, Impact Factor, Indexing), Patents (Patent Number, Status, Country), Research Grants (Funding Agency, Amount), Workshops/FDPs (Duration, Role), and Awards.
3. **Deep PDF Inspection**: Implements deep magic-byte validation (`%PDF-`), file size constraints (10 MB), and UUID filename generation to guarantee secure proof certificate storage while eliminating path traversal vulnerabilities.
4. **Automated Verification Workflows**: Facilitates multi-level verification where HODs review department submissions and Administrators manage institutional records with mandatory verification feedback.
5. **Real-Time Notification & Immutable Audit Trail**: Integrates event-driven notifications to alert stakeholders of review status changes and maintains an append-only security audit log recording every system transaction.
6. **Institutional Analytics & Data Export**: Delivers real-time category distribution, status metrics, and department comparison analytics alongside authorization-scoped CSV report generation.

Comprehensive automated testing validated system correctness across 169 test scenarios with a **100% pass rate**. The portal establishes a reliable, transparent, and scalable digital infrastructure for institutional academic excellence.
