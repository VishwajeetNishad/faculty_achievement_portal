# Future Scope & Planned Enhancements

**Institution**: Noida Institute of Engineering and Technology (NIET)  

---

## FUTURE SCOPE (Planned Post-Release Enhancements)

While the current production implementation (Steps 1–23) fulfills all initial requirements with a 100% test pass rate, the following enhancements are identified for future expansion:

1. **Email & SMS Notifications**: Integrate Spring Mail (SMTP / Amazon SES) to send instant email digests to faculty members when their achievements are approved or rejected.
2. **Native Mobile Application**: Develop iOS and Android cross-platform mobile apps using React Native or Flutter for instant camera-based certificate uploads.
3. **Cloud Object Storage (AWS S3)**: Transition file proof storage from local filesystem to encrypted Amazon S3 buckets for enhanced multi-region availability and lifecycle backup.
4. **AI-Powered OCR Document Parsing**: Integrate optical character recognition (OCR / Tesseract / AWS Textract) to automatically parse uploaded PDF certificates and pre-fill publication title, DOI, and author names.
5. **Institutional Accreditation Report Automation**: Add automated one-click PDF generation matching exact NAAC (Criterion 3), NBA, and NIRF annual data submission formats.
6. **Digital Signatures & Blockchain Verification**: Implement PKI digital signatures for verified certificates to prevent credential spoofing.
7. **Single Sign-On (SSO / OAuth2 / SAML)**: Integrate institutional Microsoft 365 / Google Workspace OAuth2 for seamless single sign-on.
8. **Real-Time WebSockets Push Alerts**: Upgrade notification polling to Spring WebSockets (STOMP) for instant browser push notifications.
