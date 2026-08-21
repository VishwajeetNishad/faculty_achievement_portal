# Technology Stack Specification

**Institution**: Noida Institute of Engineering and Technology (NIET)  

---

## 1. Backend Stack

- **Programming Language**: Java 21 LTS (OpenJDK / Eclipse Adoptium HotSpot)
- **Framework**: Spring Boot 3.3.4
- **Security Framework**: Spring Security 6.x
- **Authentication**: JJWT 0.12.6 (JSON Web Tokens - HS256 algorithm)
- **Password Hashing**: BCrypt (`BCryptPasswordEncoder` - Strength 10)
- **Data Access & Persistence**: Spring Data JPA, Hibernate ORM 6.x (`ddl-auto=validate`)
- **Validation**: Jakarta Bean Validation (`jakarta.validation-api`)
- **Build & Dependency Management**: Apache Maven (mvnd 1.0.6)
- **Embedded Web Server**: Apache Tomcat 10.1 (Port 8080)

---

## 2. Database Stack

- **Database Engine**: MySQL Server 8.0+
- **Database Driver**: MySQL Connector/J (`com.mysql.cj.jdbc.Driver`)
- **Connection Pool**: HikariCP (`spring.datasource.hikari`)

---

## 3. Frontend Stack

- **Markup**: HTML5 (Semantic elements, ARIA attributes)
- **Styling**: Vanilla CSS3 (Custom CSS Variables, Glassmorphism, Micro-animations)
- **Scripting**: Vanilla JavaScript ES6+ (Async/Await, Fetch API, DOM manipulation)
- **Fonts**: Google Fonts (Inter font family)

---

## 4. File Storage & Physical Assets

- **Storage Type**: Server Local Filesystem Directory (`uploads/achievements`)
- **File Format**: Portable Document Format (PDF)
- **Validation Engine**: Stream Magic-Byte Header Validation (`%PDF-`)

---

## 5. Production Infrastructure

- **Operating System**: Linux (Ubuntu 22.04 LTS) / Windows Server 2022
- **Reverse Proxy**: Nginx 1.18+ (SSL/TLS HTTPS Termination)
- **Service Manager**: Linux systemd (`niet-faculty.service`)
- **SSL Certificate**: Let's Encrypt X.509 Certificate
