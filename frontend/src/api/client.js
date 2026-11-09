import axios from 'axios';

const BASE = import.meta.env.VITE_API_URL || '';

// The short-lived access token lives only in this variable (memory). It is never written to localStorage, so a
// script injected into the page cannot read it back later, and it disappears when the tab closes. The long-lived
// refresh token is an HttpOnly cookie that JavaScript cannot see at all; the browser attaches it by itself.
let accessToken = null;
export const session = {
  set(token) { accessToken = token; },
  clear() { accessToken = null; },
};

const api = axios.create({ baseURL: BASE, withCredentials: true });

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

let refreshing = null;

/**
 * Exchanges the refresh cookie for a new access token (and a rotated cookie). Calls made at the same moment
 * share one request, because each refresh token works only once. Used on page load and when a token expires.
 */
export function refreshSession() {
  refreshing = refreshing || axios.post(`${BASE}/api/auth/refresh`, null, { withCredentials: true })
    .then(({ data }) => { accessToken = data.accessToken; return data; })
    .finally(() => { refreshing = null; });
  return refreshing;
}

const PUBLIC = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout',
                '/api/auth/forgot-password', '/api/auth/reset-password'];

// When a request fails with 401 (the 15-minute access token expired), refresh once and retry it.
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config;
    const expired = error.response?.status === 401 && original && !original._retried
      && !PUBLIC.some((p) => original.url?.includes(p));
    if (!expired) return Promise.reject(error);

    original._retried = true;
    try {
      const { accessToken: fresh } = await refreshSession();
      original.headers.Authorization = `Bearer ${fresh}`;
      return api(original);
    } catch {
      accessToken = null;
      window.dispatchEvent(new Event('sw:signed-out'));   // AuthContext reacts by showing the login page
      return Promise.reject(error);
    }
  },
);

/** The message the backend put in its standard error body, or a friendly fallback. */
export function errorMessage(err) {
  if (!err.response) return 'Cannot reach the server. Is the backend running?';
  return err.response.data?.message || `Something went wrong (${err.response.status}).`;
}

export default api;
