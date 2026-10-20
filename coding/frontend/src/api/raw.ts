import axios from 'axios';
import { runtimeConfig } from '@/config';

const options = { withCredentials: true } as const;

export const rawAuth = axios.create({ ...options, baseURL: runtimeConfig.authApiUrl });
export const rawUser = axios.create({ ...options, baseURL: runtimeConfig.userApiUrl });
export const rawTask = axios.create({ ...options, baseURL: runtimeConfig.taskApiUrl });
export const rawTimeline = axios.create({ ...options, baseURL: runtimeConfig.timelineApiUrl });
