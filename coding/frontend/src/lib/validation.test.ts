import { describe, expect, it } from 'vitest';
import {
  changePasswordSchema,
  createUserSchema,
  holidaySchema,
  loginSchema,
  passwordPolicy,
  passwordSchema,
  projectSchema,
  resetPasswordSchema,
  taskSchema,
  timelineSchema,
  userSchema,
  userUpdateSchema,
} from '@/lib/validation';

const uuid = '11111111-1111-4111-8111-111111111111';

describe('validation schemas', () => {
  it('validates login credentials', () => {
    expect(loginSchema.safeParse({ username: ' jane ', password: 'secret' }).success).toBe(true);
    expect(loginSchema.safeParse({ username: '   ', password: 'secret' }).success).toBe(false);
    expect(loginSchema.safeParse({ username: 'jane', password: '' }).success).toBe(false);
    expect(loginSchema.safeParse({ username: 'j', password: 'x'.repeat(201) }).success).toBe(false);
  });

  it('validates password policy and password changes', () => {
    expect(passwordPolicy.safeParse('Passw0rd').success).toBe(true);
    expect(passwordPolicy.safeParse('short1').success).toBe(false);
    expect(passwordPolicy.safeParse('a'.repeat(65) + '1').success).toBe(false);
    expect(passwordSchema.safeParse({ newPassword: 'Passw0rd' }).success).toBe(true);
    expect(passwordSchema.safeParse({ newPassword: 'onlyletters' }).success).toBe(false);
    expect(passwordSchema.safeParse({ newPassword: '12345678' }).success).toBe(false);

    expect(
      changePasswordSchema.safeParse({ currentPassword: 'OldPass1', newPassword: 'NewPass2' }).success,
    ).toBe(true);
    expect(
      changePasswordSchema.safeParse({ currentPassword: '', newPassword: 'NewPass2' }).success,
    ).toBe(false);
    expect(
      changePasswordSchema.safeParse({ currentPassword: 'SamePass1', newPassword: 'SamePass1' })
        .success,
    ).toBe(false);

    expect(resetPasswordSchema.safeParse({ newPassword: 'NewPass2', confirmPassword: 'NewPass2' }).success).toBe(
      true,
    );
    expect(resetPasswordSchema.safeParse({ newPassword: 'NewPass2', confirmPassword: 'OtherPass3' }).success).toBe(
      false,
    );
    expect(resetPasswordSchema.safeParse({ newPassword: 'NewPass2', confirmPassword: '' }).success).toBe(false);
  });

  it('validates user forms', () => {
    const valid = {
      username: 'jane.doe',
      email: 'jane@example.com',
      fullName: 'Jane Doe',
      roles: ['USER'],
    };
    expect(userSchema.safeParse(valid).success).toBe(true);
    expect(userSchema.safeParse({ ...valid, username: 'ab' }).success).toBe(false);
    expect(userSchema.safeParse({ ...valid, username: 'UPPER' }).success).toBe(false);
    expect(userSchema.safeParse({ ...valid, email: 'nope' }).success).toBe(false);
    expect(userSchema.safeParse({ ...valid, fullName: ' ' }).success).toBe(false);
    expect(userSchema.safeParse({ ...valid, roles: [] }).success).toBe(false);
    expect(userSchema.safeParse({ ...valid, roles: ['ADMIN', 'USER', 'ADMIN'] }).success).toBe(false);
    expect(userUpdateSchema.safeParse({ email: valid.email, fullName: valid.fullName }).success).toBe(true);

    const created = {
      ...valid,
      password: 'Passw0rd',
      confirmPassword: 'Passw0rd',
    };
    expect(createUserSchema.safeParse(created).success).toBe(true);
    expect(createUserSchema.safeParse({ ...created, confirmPassword: 'OtherPass2' }).success).toBe(false);
    expect(createUserSchema.safeParse({ ...created, confirmPassword: '' }).success).toBe(false);
  });

  it('validates project forms', () => {
    const valid = {
      code: 'PRJ-1',
      name: 'Project',
      description: '',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      memberIds: [uuid],
    };
    expect(projectSchema.safeParse(valid).success).toBe(true);
    expect(projectSchema.safeParse({ ...valid, code: 'x' }).success).toBe(false);
    expect(projectSchema.safeParse({ ...valid, code: 'lower' }).success).toBe(false);
    expect(projectSchema.safeParse({ ...valid, name: ' ' }).success).toBe(false);
    expect(projectSchema.safeParse({ ...valid, description: 'x'.repeat(2001) }).success).toBe(false);
    expect(projectSchema.safeParse({ ...valid, startDate: '' }).success).toBe(false);
    expect(projectSchema.safeParse({ ...valid, endDate: '' }).success).toBe(false);
    expect(projectSchema.safeParse({ ...valid, endDate: '2025-12-31' }).success).toBe(false);
  });

  it('validates task forms including closed tasks', () => {
    const valid = {
      title: 'Task',
      description: '',
      startDate: '2026-01-01',
      endDate: '2026-01-02',
      picUserId: uuid,
      status: 'OPEN',
      percentComplete: 40,
    };
    expect(taskSchema.safeParse(valid).success).toBe(true);
    expect(taskSchema.safeParse({ ...valid, title: ' ' }).success).toBe(false);
    expect(taskSchema.safeParse({ ...valid, description: 'x'.repeat(2001) }).success).toBe(false);
    expect(taskSchema.safeParse({ ...valid, startDate: '' }).success).toBe(false);
    expect(taskSchema.safeParse({ ...valid, endDate: '' }).success).toBe(false);
    expect(taskSchema.safeParse({ ...valid, endDate: '2025-12-31' }).success).toBe(false);
    expect(taskSchema.safeParse({ ...valid, picUserId: 'not-a-uuid' }).success).toBe(false);
    expect(taskSchema.safeParse({ ...valid, status: 'DONE' }).success).toBe(false);
    expect(taskSchema.safeParse({ ...valid, percentComplete: -1 }).success).toBe(false);
    expect(taskSchema.safeParse({ ...valid, percentComplete: 101 }).success).toBe(false);
    expect(taskSchema.safeParse({ ...valid, percentComplete: '55' }).success).toBe(true);
    expect(taskSchema.safeParse({ ...valid, percentComplete: 40.5 }).success).toBe(false);
    expect(taskSchema.safeParse({ ...valid, status: 'CLOSED', percentComplete: 100 }).success).toBe(true);
    expect(taskSchema.safeParse({ ...valid, status: 'CLOSED', percentComplete: 99 }).success).toBe(false);
  });

  it('validates timeline forms; horizon is enforced by the backend', () => {
    expect(
      timelineSchema.safeParse({
        startDate: '2026-01-01',
        endDate: '2026-01-31',
        revisionNotes: 'Plan',
      }).success,
    ).toBe(true);
    expect(
      timelineSchema.safeParse({ startDate: '2026-01-01', endDate: '2026-01-01' }).success,
    ).toBe(true);
    expect(timelineSchema.safeParse({ startDate: '', endDate: '2026-01-01' }).success).toBe(false);
    expect(timelineSchema.safeParse({ startDate: '2026-01-01', endDate: '' }).success).toBe(false);
    expect(
      timelineSchema.safeParse({ startDate: '2026-02-01', endDate: '2026-01-01' }).success,
    ).toBe(false);
    expect(
      timelineSchema.safeParse({ startDate: '2024-12-31', endDate: '2025-01-02' }).success,
    ).toBe(true);
    expect(
      timelineSchema.safeParse({ startDate: '2027-12-30', endDate: '2028-01-02' }).success,
    ).toBe(true);
    expect(
      timelineSchema.safeParse({
        startDate: '2026-01-01',
        endDate: '2026-01-02',
        revisionNotes: 'x'.repeat(1001),
      }).success,
    ).toBe(false);
  });

  it('validates holidays', () => {
    expect(
      holidaySchema.safeParse({ date: '2026-08-17', name: 'Independence Day', type: 'NATIONAL' })
        .success,
    ).toBe(true);
    expect(holidaySchema.safeParse({ date: '', name: 'Holiday', type: 'NATIONAL' }).success).toBe(false);
    expect(holidaySchema.safeParse({ date: '2026-08-17', name: ' ', type: 'NATIONAL' }).success).toBe(false);
    expect(
      holidaySchema.safeParse({ date: '2026-08-17', name: 'x'.repeat(151), type: 'NATIONAL' }).success,
    ).toBe(false);
    expect(
      holidaySchema.safeParse({ date: '2026-08-17', name: 'Holiday', type: 'OTHER' }).success,
    ).toBe(false);
  });
});
