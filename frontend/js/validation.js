/**
 * Faculty Achievement Portal — Reusable Client-Side Form Validation Module
 */

const FormValidator = (() => {

  const validateRequired = (val) => val !== null && val !== undefined && String(val).trim() !== '';

  const validateEmail = (email) => {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return re.test(String(email).toLowerCase());
  };

  const validateDate = (dateStr) => {
    if (!dateStr) return false;
    const d = new Date(dateStr);
    return !isNaN(d.getTime());
  };

  const validatePositiveNumber = (val) => {
    if (val === '' || val === null || val === undefined) return true;
    const num = Number(val);
    return !isNaN(num) && num >= 0;
  };

  const validateFileExtension = (fileName, allowedExts = ['pdf', 'png', 'jpg', 'jpeg']) => {
    if (!fileName) return true;
    const ext = fileName.split('.').pop().toLowerCase();
    return allowedExts.includes(ext);
  };

  const validateFileSize = (file, maxMB = 5) => {
    if (!file) return true;
    const maxBytes = maxMB * 1024 * 1024;
    return file.size <= maxBytes;
  };

  return {
    validateRequired,
    validateEmail,
    validateDate,
    validatePositiveNumber,
    validateFileExtension,
    validateFileSize
  };

})();
