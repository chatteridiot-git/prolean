import { z } from 'zod';

const password = z
  .string()
  .min(8, 'At least 8 characters')
  .max(64, 'At most 64 characters')
  .regex(/[A-Za-z]/, 'Include a letter')
  .regex(/[0-9]/, 'Include a digit');

export const loginSchema = z.object({
  username: z.string().trim().min(1, 'Username is required').max(50),
  password: z.string().min(1, 'Password is required').max(200),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const passwordSchema = z.object({ newPassword: password });
export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is required'),
    newPassword: password,
  })
  .refine((data) => data.currentPassword !== data.newPassword, {
    message: 'New password must differ from the current password',
    path: ['newPassword'],
  });

export const userSchema = z.object({
  username: z
    .string()
    .trim()
    .min(3, 'At least 3 characters')
    .max(50)
    .regex(/^[a-z0-9._-]+$/, 'Use lowercase letters, numbers, dot, underscore or dash'),
  email: z.string().trim().email('Enter a valid email').max(255),
  fullName: z.string().trim().min(1, 'Full name is required').max(100),
  roles: z.array(z.enum(['ADMIN', 'USER'])).min(1, 'Choose at least one role').max(2),
});
export type UserValues = z.infer<typeof userSchema>;

export const userUpdateSchema = userSchema.pick({ email: true, fullName: true });

export const createUserSchema = userSchema
  .extend({
    password,
    confirmPassword: z.string().min(1, 'Confirm the password'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });
export type CreateUserValues = z.infer<typeof createUserSchema>;

export const resetPasswordSchema = z
  .object({ newPassword: password, confirmPassword: z.string().min(1, 'Confirm the password') })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;

export const projectSchema = z
  .object({
    code: z
      .string()
      .trim()
      .min(2, 'At least 2 characters')
      .max(30)
      .regex(/^[A-Z0-9-]+$/, 'Use uppercase letters, numbers or dash'),
    name: z.string().trim().min(1, 'Name is required').max(150),
    description: z.string().max(2000).optional(),
    startDate: z.string().min(1, 'Start date is required'),
    endDate: z.string().min(1, 'End date is required'),
    memberIds: z.array(z.string()).max(200).optional(),
  })
  .refine((data) => data.endDate >= data.startDate, {
    message: 'End date must be on or after start date',
    path: ['endDate'],
  });
export type ProjectValues = z.infer<typeof projectSchema>;

export const taskSchema = z
  .object({
    title: z.string().trim().min(1, 'Title is required').max(200),
    description: z.string().max(2000).optional(),
    startDate: z.string().min(1, 'Start date is required'),
    endDate: z.string().min(1, 'End date is required'),
    picUserId: z.string().uuid('Choose a PIC'),
    status: z.enum(['OPEN', 'IN_PROGRESS', 'CLOSED']),
    percentComplete: z.coerce.number().int().min(0).max(100),
  })
  .refine((data) => data.endDate >= data.startDate, {
    message: 'End date must be on or after start date',
    path: ['endDate'],
  })
  .refine((data) => data.status !== 'CLOSED' || data.percentComplete === 100, {
    message: 'Closed tasks must be 100% complete',
    path: ['percentComplete'],
  });
export type TaskValues = z.infer<typeof taskSchema>;

export const timelineSchema = z
  .object({
    startDate: z.string().min(1, 'Start date is required'),
    endDate: z.string().min(1, 'End date is required'),
    revisionNotes: z.string().max(1000).optional(),
  })
  .refine((data) => data.endDate >= data.startDate, {
    message: 'End date must be on or after start date',
    path: ['endDate'],
  })
  .refine((data) => data.startDate >= '2025-01-01' && data.endDate <= '2027-12-31', {
    message: 'Dates must be within the covered holiday horizon (2025–2027)',
    path: ['startDate'],
  });
export type TimelineValues = z.infer<typeof timelineSchema>;

export const holidaySchema = z.object({
  date: z.string().min(1, 'Date is required'),
  name: z.string().trim().min(1, 'Name is required').max(150),
  type: z.enum(['NATIONAL', 'JOINT_LEAVE']),
});
export type HolidayValues = z.infer<typeof holidaySchema>;

export const passwordPolicy = password;
