import { describe, it, expect } from 'vitest';
import {
  validateEmail,
  validatePhone,
  validateRequired,
  validateLength,
  validateNumeric,
  createFormValidators,
  formatFormData,
  sanitizeFormData,
} from './form';

describe('Form Utilities', () => {
  describe('validateEmail', () => {
    it('should validate correct email formats', () => {
      const validEmails = [
        'test@example.com',
        'user.name@domain.co.uk',
        'user+tag@example.org',
        'admin@company.com.cn',
      ];

      validEmails.forEach((email) => {
        expect(validateEmail(email)).toBe(true);
      });
    });

    it('should reject invalid email formats', () => {
      const invalidEmails = [
        'invalid.email',
        '@domain.com',
        'user@',
        'user space@domain.com',
        'user@domain',
        '',
        null,
        undefined,
      ];

      invalidEmails.forEach((email) => {
        expect(validateEmail(email as string)).toBe(false);
      });
    });
  });

  describe('validatePhone', () => {
    it('should validate correct Chinese phone numbers', () => {
      const validPhones = ['13812345678', '15987654321', '18612345678', '13000000000'];

      validPhones.forEach((phone) => {
        expect(validatePhone(phone)).toBe(true);
      });
    });

    it('should reject invalid phone numbers', () => {
      const invalidPhones = [
        '12345678901', // starts with 1 but not valid prefix
        '1381234567', // too short
        '138123456789', // too long
        '02812345678', // landline format
        'abcd1234567', // contains letters
        '',
        null,
        undefined,
      ];

      invalidPhones.forEach((phone) => {
        expect(validatePhone(phone as string)).toBe(false);
      });
    });

    it('should validate international format when enabled', () => {
      expect(validatePhone('+8613812345678', { international: true })).toBe(true);
      expect(validatePhone('+1234567890', { international: true })).toBe(true);
    });
  });

  describe('validateRequired', () => {
    it('should validate non-empty values', () => {
      expect(validateRequired('text')).toBe(true);
      expect(validateRequired(123)).toBe(true);
      expect(validateRequired(0)).toBe(true);
      expect(validateRequired(false)).toBe(true);
      expect(validateRequired([])).toBe(true);
      expect(validateRequired({})).toBe(true);
    });

    it('should reject empty values', () => {
      expect(validateRequired('')).toBe(false);
      expect(validateRequired('   ')).toBe(false); // whitespace only
      expect(validateRequired(null)).toBe(false);
      expect(validateRequired(undefined)).toBe(false);
    });

    it('should handle arrays and objects', () => {
      expect(validateRequired([1, 2, 3])).toBe(true);
      expect(validateRequired([])).toBe(true);
      expect(validateRequired({ key: 'value' })).toBe(true);
      expect(validateRequired({})).toBe(true);
    });
  });

  describe('validateLength', () => {
    it('should validate string length', () => {
      expect(validateLength('hello', { min: 3, max: 10 })).toBe(true);
      expect(validateLength('ab', { min: 3 })).toBe(false);
      expect(validateLength('very long string', { max: 10 })).toBe(false);
    });

    it('should validate exact length', () => {
      expect(validateLength('12345', { exact: 5 })).toBe(true);
      expect(validateLength('1234', { exact: 5 })).toBe(false);
    });

    it('should handle edge cases', () => {
      expect(validateLength('', { min: 0 })).toBe(true);
      expect(validateLength('', { min: 1 })).toBe(false);
      expect(validateLength(null as any, { min: 1 })).toBe(false);
    });
  });

  describe('validateNumeric', () => {
    it('should validate numeric strings', () => {
      expect(validateNumeric('123')).toBe(true);
      expect(validateNumeric('123.45')).toBe(true);
      expect(validateNumeric('-123')).toBe(true);
      expect(validateNumeric('0')).toBe(true);
    });

    it('should reject non-numeric strings', () => {
      expect(validateNumeric('abc')).toBe(false);
      expect(validateNumeric('12a3')).toBe(false);
      expect(validateNumeric('')).toBe(false);
      expect(validateNumeric('12.34.56')).toBe(false);
    });

    it('should validate integer only when specified', () => {
      expect(validateNumeric('123', { integer: true })).toBe(true);
      expect(validateNumeric('123.45', { integer: true })).toBe(false);
    });

    it('should validate positive numbers when specified', () => {
      expect(validateNumeric('123', { positive: true })).toBe(true);
      expect(validateNumeric('-123', { positive: true })).toBe(false);
      expect(validateNumeric('0', { positive: true })).toBe(false);
    });
  });

  describe('createFormValidators', () => {
    it('should create form validators with rules', () => {
      const validators = createFormValidators({
        email: [
          { required: true, message: '请输入邮箱' },
          { type: 'email', message: '邮箱格式不正确' },
        ],
        phone: [
          { required: true, message: '请输入手机号' },
          { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
        ],
      });

      expect(validators).toHaveProperty('email');
      expect(validators).toHaveProperty('phone');
      expect(Array.isArray(validators.email)).toBe(true);
      expect(Array.isArray(validators.phone)).toBe(true);
    });

    it('should create validators with custom validation functions', () => {
      const customValidator = (value: string) => {
        if (value === 'forbidden') {
          return Promise.reject('此值不被允许');
        }
        return Promise.resolve();
      };

      const validators = createFormValidators({
        custom: [{ validator: customValidator }],
      });

      expect(validators.custom).toBeDefined();
    });
  });

  describe('formatFormData', () => {
    it('should format form data with transformers', () => {
      const formData = {
        name: '  张三  ',
        email: '  USER@EXAMPLE.COM  ',
        age: '25',
        tags: ['tag1', 'tag2'],
      };

      const formatters = {
        name: (value: string) => value.trim(),
        email: (value: string) => value.trim().toLowerCase(),
        age: (value: string) => parseInt(value, 10),
        tags: (value: string[]) => value.filter(Boolean),
      };

      const formatted = formatFormData(formData, formatters);

      expect(formatted).toEqual({
        name: '张三',
        email: 'user@example.com',
        age: 25,
        tags: ['tag1', 'tag2'],
      });
    });

    it('should handle missing formatters', () => {
      const formData = { name: 'test', value: 123 };
      const formatters = { name: (value: string) => value.toUpperCase() };

      const formatted = formatFormData(formData, formatters);

      expect(formatted).toEqual({
        name: 'TEST',
        value: 123,
      });
    });

    it('should handle empty form data', () => {
      const formatted = formatFormData({}, {});
      expect(formatted).toEqual({});
    });
  });

  describe('sanitizeFormData', () => {
    it('should remove empty and invalid values', () => {
      const formData = {
        name: 'valid',
        email: '',
        phone: '   ',
        age: 0,
        active: false,
        tags: [],
        meta: null,
        extra: undefined,
      };

      const sanitized = sanitizeFormData(formData);

      expect(sanitized).toEqual({
        name: 'valid',
        age: 0,
        active: false,
        tags: [],
      });
    });

    it('should preserve specified fields even if empty', () => {
      const formData = {
        name: '',
        required: '',
        optional: '',
      };

      const sanitized = sanitizeFormData(formData, {
        keepEmpty: ['required'],
      });

      expect(sanitized).toEqual({
        required: '',
      });
    });

    it('should trim string values when specified', () => {
      const formData = {
        name: '  张三  ',
        description: '  测试内容  ',
      };

      const sanitized = sanitizeFormData(formData, {
        trimStrings: true,
      });

      expect(sanitized).toEqual({
        name: '张三',
        description: '测试内容',
      });
    });

    it('should handle nested objects', () => {
      const formData = {
        user: {
          name: 'test',
          email: '',
          profile: {
            bio: '   ',
            age: 25,
          },
        },
        tags: ['valid', '', 'another'],
      };

      const sanitized = sanitizeFormData(formData, {
        trimStrings: true,
        removeEmpty: true,
      });

      expect(sanitized.user.name).toBe('test');
      expect(sanitized.user).not.toHaveProperty('email');
      expect(sanitized.user.profile.age).toBe(25);
    });
  });
});
