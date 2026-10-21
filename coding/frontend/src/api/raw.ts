import axios from 'axios';
import { runtimeConfig } from '@/config';

const authOptions = { withCredentials: true } as const;
const apiOptions = { withCredentials: false } as const;

export const rawAuth = axios.create({ ...authOptions, baseURL: runtimeConfig.authApiUrl });
export const rawUser = axios.create({ ...apiOptions, baseURL: runtimeConfig.userApiUrl });
export const rawTask = axios.create({ ...apiOptions, baseURL: runtimeConfig.taskApiUrl });
export const rawTimeline = axios.create({ ...apiOptions, baseURL: runtimeConfig.timelineApiUrl });
