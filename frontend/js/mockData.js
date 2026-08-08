/**
 * Faculty Achievement Portal — Centralized Client-Side Mock Data Store
 * NOTE: Step 11 Pure Frontend State Management (Zero API / Zero DB connection)
 */

const MockStore = (() => {

  // Initial Seed Faculty User
  const facultyProfile = {
    id: 1,
    employeeId: "EMP001",
    fullName: "Dr. Sharma",
    email: "sharma@niet.co.in",
    department: "Computer Science & Engineering",
    departmentCode: "CSE",
    designation: "Professor & Research Coordinator",
    role: "FACULTY",
    joiningDate: "2018-07-15"
  };

  // Initial Seed Achievements Dataset
  let achievements = [
    {
      id: 101,
      userId: 1,
      facultyName: "Dr. Sharma",
      department: "CSE",
      category: "PUBLICATION",
      categoryLabel: "Research Publication",
      title: "Deep Learning in Healthcare Systems",
      description: "Published high-impact research paper on AI medical image diagnosis in IEEE Transactions.",
      journalName: "IEEE Transactions on Medical Imaging",
      indexing: "SCI",
      impactFactor: 4.85,
      doi: "10.1109/TMI.2025.12345",
      achievementDate: "2025-05-10",
      academicYear: "2025-2026",
      status: "PENDING",
      proofDocumentUrl: "https://example.com/certificates/ieee-paper.pdf",
      createdAt: "2025-05-10T10:30:00"
    },
    {
      id: 102,
      userId: 1,
      facultyName: "Dr. Sharma",
      department: "CSE",
      category: "PUBLICATION",
      categoryLabel: "Research Publication",
      title: "AI in Medical Image Segmentation",
      description: "Comparative study of U-Net variants for tumor segmentation published in Springer Nature.",
      journalName: "Springer Nature Medical Informatics",
      indexing: "SCOPUS",
      impactFactor: 3.42,
      doi: "10.1007/s11517-025-0312",
      achievementDate: "2025-06-15",
      academicYear: "2025-2026",
      status: "APPROVED",
      proofDocumentUrl: "https://example.com/certificates/springer-paper.pdf",
      createdAt: "2025-06-15T14:15:00"
    },
    {
      id: 103,
      userId: 1,
      facultyName: "Dr. Sharma",
      department: "CSE",
      category: "PATENT",
      categoryLabel: "Patent / Intellectual Property",
      title: "Smart Edge Sensor Node for Agricultural Monitoring",
      description: "IoT-based soil moisture and nutrient detection system filed with Indian Patent Office.",
      patentNumber: "202511098765",
      patentStatus: "FILED",
      filingDate: "2025-03-20",
      achievementDate: "2025-03-20",
      academicYear: "2024-2025",
      status: "APPROVED",
      proofDocumentUrl: "https://example.com/patents/202511098765.pdf",
      createdAt: "2025-03-20T11:00:00"
    },
    {
      id: 104,
      userId: 1,
      facultyName: "Dr. Sharma",
      department: "CSE",
      category: "RESEARCH_GRANT",
      categoryLabel: "Research Grant",
      title: "Autonomous Drone Swarm Navigation in GPS-Denied Environments",
      description: "Research grant sanctioned by DST SERB under Core Research Grant scheme.",
      fundingAgency: "DST SERB",
      grantAmount: 1850000,
      grantStatus: "SANCTIONED",
      achievementDate: "2024-11-05",
      academicYear: "2024-2025",
      status: "APPROVED",
      proofDocumentUrl: "https://example.com/grants/serb-approval.pdf",
      createdAt: "2024-11-05T09:45:00"
    },
    {
      id: 105,
      userId: 2,
      facultyName: "Dr. Verma",
      department: "ECE",
      category: "WORKSHOP_FDP",
      categoryLabel: "Workshop / FDP",
      title: "National FDP on VLSI Design & Microelectronics",
      description: "One week AICTE ATAL FDP organized at IIT Delhi.",
      organizingBody: "IIT Delhi & AICTE ATAL",
      eventRole: "PARTICIPANT",
      durationDays: 5,
      achievementDate: "2025-01-12",
      academicYear: "2024-2025",
      status: "REJECTED",
      verificationComment: "Incomplete certificate copy attached. Please resubmit clear PDF.",
      proofDocumentUrl: "https://example.com/fdp/iitd-cert.pdf",
      createdAt: "2025-01-12T16:20:00"
    }
  ];

  // Faculty Roster Dataset for Admin
  const facultyRoster = [
    { id: 1, employeeId: "EMP001", name: "Dr. Sharma", email: "sharma@niet.co.in", department: "CSE", designation: "Professor", status: "ACTIVE" },
    { id: 2, employeeId: "EMP002", name: "Dr. Verma", email: "verma@niet.co.in", department: "ECE", designation: "Associate Professor", status: "ACTIVE" },
    { id: 3, employeeId: "EMP003", name: "Dr. Gupta", email: "gupta@niet.co.in", department: "ME", designation: "Assistant Professor", status: "ACTIVE" }
  ];

  return {
    getFacultyProfile: () => ({ ...facultyProfile }),
    updateFacultyProfile: (updatedData) => {
      Object.assign(facultyProfile, updatedData);
      return { ...facultyProfile };
    },
    getAchievements: () => [...achievements],
    getAchievementById: (id) => achievements.find(a => a.id === Number(id)),
    addAchievement: (newRecord) => {
      const created = {
        id: Date.now(),
        userId: facultyProfile.id,
        facultyName: facultyProfile.fullName,
        department: facultyProfile.departmentCode,
        status: "PENDING",
        createdAt: new Date().toISOString(),
        ...newRecord
      };
      achievements.unshift(created);
      return created;
    },
    deleteAchievement: (id) => {
      achievements = achievements.filter(a => a.id !== Number(id));
      return true;
    },
    updateAchievementStatus: (id, newStatus, comment = "") => {
      const item = achievements.find(a => a.id === Number(id));
      if (item) {
        item.status = newStatus;
        item.verificationComment = comment;
        item.verifiedAt = new Date().toISOString();
        return item;
      }
      return null;
    },
    getFacultyRoster: () => [...facultyRoster]
  };

})();
