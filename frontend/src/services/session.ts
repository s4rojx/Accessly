import type { User } from '../types';

let currentUser: User | null = null;

export function getCurrentUser() { return currentUser; }
export function setCurrentUser(user: User | null) { currentUser = user; }
