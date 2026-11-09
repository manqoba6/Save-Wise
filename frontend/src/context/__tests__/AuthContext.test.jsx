import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/client.js', () => ({
  default: { post: vi.fn() },
  refreshSession: vi.fn(),
  session: { set: vi.fn(), clear: vi.fn() },
}));

import api, { refreshSession, session } from '../../api/client.js';
import { AuthProvider, useAuth } from '../AuthContext.jsx';

const amahle = { id: 1, firstName: 'Amahle', lastName: 'Dlamini', email: 'a@b.test', role: 'USER' };

function Probe() {
  const { user, loading, login, logout, updateUser } = useAuth();
  if (loading) return <p>checking</p>;
  return (
    <>
      <p>{user ? `signed in as ${user.firstName}` : 'signed out'}</p>
      <button onClick={() => login('a@b.test', 'pw')}>login</button>
      <button onClick={logout}>logout</button>
      <button onClick={() => updateUser({ firstName: 'Zinhle' })}>rename</button>
    </>
  );
}
const show = () => render(<AuthProvider><Probe /></AuthProvider>);

beforeEach(() => vi.clearAllMocks());

describe('AuthProvider', () => {
  it('restores the session from the cookie when the page loads', async () => {
    refreshSession.mockResolvedValue({ accessToken: 't', user: amahle });
    show();
    expect(screen.getByText('checking')).toBeTruthy();
    expect(await screen.findByText('signed in as Amahle')).toBeTruthy();
  });

  it('starts signed out when there is no valid cookie', async () => {
    refreshSession.mockRejectedValue(new Error('401'));
    show();
    expect(await screen.findByText('signed out')).toBeTruthy();
  });

  it('login keeps the access token in memory and shows the user', async () => {
    refreshSession.mockRejectedValue(new Error('401'));
    api.post.mockResolvedValue({ data: { accessToken: 'abc', user: amahle } });
    show();
    await userEvent.click(await screen.findByText('login'));
    await waitFor(() => expect(session.set).toHaveBeenCalledWith('abc'));
    expect(await screen.findByText('signed in as Amahle')).toBeTruthy();
    expect(api.post).toHaveBeenCalledWith('/api/auth/login', { email: 'a@b.test', password: 'pw' });
  });

  it('logout calls the server (which clears the cookie) and forgets the token', async () => {
    refreshSession.mockResolvedValue({ accessToken: 't', user: amahle });
    api.post.mockResolvedValue({});
    show();
    await userEvent.click(await screen.findByText('logout'));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/auth/logout'));
    expect(session.clear).toHaveBeenCalled();
    expect(await screen.findByText('signed out')).toBeTruthy();
  });

  it('signs out when the client reports that the session could not be refreshed', async () => {
    refreshSession.mockResolvedValue({ accessToken: 't', user: amahle });
    show();
    await screen.findByText('signed in as Amahle');
    window.dispatchEvent(new Event('sw:signed-out'));
    expect(await screen.findByText('signed out')).toBeTruthy();
  });

  it('updateUser changes the name shown in the header', async () => {
    refreshSession.mockResolvedValue({ accessToken: 't', user: amahle });
    show();
    await userEvent.click(await screen.findByText('rename'));
    expect(await screen.findByText('signed in as Zinhle')).toBeTruthy();
  });
});
