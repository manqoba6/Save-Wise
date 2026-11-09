import axios, { AxiosError } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import api, { refreshSession, session } from '../client.js';

const user = { id: 1, firstName: 'Amahle', email: 'a@b.test', role: 'USER' };
let refreshCalls;
let signedOut;

/** A tiny fake server: /api/auth/refresh issues "fresh", and /api/data only accepts the token "fresh". */
function fakeServer({ refreshWorks = true } = {}) {
  const adapter = async (config) => {
    const ok = (data) => ({ data, status: 200, statusText: 'OK', headers: {}, config });
    const fail = (status) => Promise.reject(new AxiosError(`HTTP ${status}`, 'ERR', config, null, { status, data: {}, headers: {}, config }));
    if (config.url.endsWith('/api/auth/refresh')) {
      refreshCalls += 1;
      await new Promise((r) => setTimeout(r, 20));      // slow enough for parallel calls to overlap
      return refreshWorks ? ok({ accessToken: 'fresh', user }) : fail(401);
    }
    return config.headers.Authorization === 'Bearer fresh' ? ok({ items: [1] }) : fail(401);
  };
  api.defaults.adapter = adapter;
  axios.defaults.adapter = adapter;
}

beforeEach(() => {
  refreshCalls = 0;
  signedOut = vi.fn();
  window.addEventListener('sw:signed-out', signedOut);
  session.clear();
});
afterEach(() => window.removeEventListener('sw:signed-out', signedOut));

describe('api client', () => {
  it('never writes tokens to browser storage', async () => {
    fakeServer();
    session.set('secret-access-token');
    await api.get('/api/data').catch(() => {});
    await refreshSession();
    expect(JSON.stringify({ ...localStorage })).not.toContain('token');
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('token');
    expect(localStorage.length).toBe(0);
  });

  it('sends the access token as a Bearer header', async () => {
    fakeServer();
    session.set('fresh');
    const res = await api.get('/api/data');
    expect(res.data.items).toEqual([1]);
  });

  it('refreshes once and retries when the access token has expired', async () => {
    fakeServer();
    session.set('expired');
    const res = await api.get('/api/data');
    expect(res.data.items).toEqual([1]);
    expect(refreshCalls).toBe(1);
  });

  it('shares ONE refresh between requests that fail at the same time (refresh tokens are single-use)', async () => {
    fakeServer();
    session.set('expired');
    const results = await Promise.all([api.get('/api/data'), api.get('/api/data'), api.get('/api/data')]);
    expect(results.every((r) => r.status === 200)).toBe(true);
    expect(refreshCalls).toBe(1);
  });

  it('signs the user out when the refresh fails', async () => {
    fakeServer({ refreshWorks: false });
    session.set('expired');
    await expect(api.get('/api/data')).rejects.toBeTruthy();
    expect(signedOut).toHaveBeenCalledTimes(1);
  });

  it('does not try to refresh after a failed login', async () => {
    fakeServer();
    const adapter = async (config) => Promise.reject(new AxiosError('bad', 'ERR', config, null, { status: 401, data: {}, headers: {}, config }));
    api.defaults.adapter = adapter;
    await expect(api.post('/api/auth/login', {})).rejects.toBeTruthy();
    expect(refreshCalls).toBe(0);
  });
});
