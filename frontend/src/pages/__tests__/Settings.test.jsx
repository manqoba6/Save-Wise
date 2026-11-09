import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const save = vi.fn();
const updateUser = vi.fn();
const login = vi.fn();
let settings;

vi.mock('../../api/client.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  errorMessage: (e) => e.response?.data?.message || 'failed',
  storage: {},
}));
vi.mock('../../context/SettingsContext.jsx', () => ({ useSettings: () => ({ settings, save, money: (v) => `R${v}` }) }));
vi.mock('../../context/AuthContext.jsx', () => ({
  useAuth: () => ({ user: { id: 1, email: 'amahle@savewise.test', firstName: 'Amahle' }, updateUser, login }),
}));

import api from '../../api/client.js';
import Settings from '../Settings.jsx';

const profile = { id: 1, firstName: 'Amahle', lastName: 'Dlamini', email: 'amahle@savewise.test', phone: null, monthlyIncome: null, hasProfilePicture: false };
const currencies = [{ code: 'ZAR', name: 'South African Rand' }, { code: 'USD', name: 'US Dollar' }];

beforeEach(() => {
  vi.clearAllMocks();
  settings = { theme: 'LIGHT', currency: 'ZAR', notificationsEnabled: true };
  save.mockResolvedValue();
  URL.createObjectURL = vi.fn(() => 'blob:picture');
  URL.revokeObjectURL = vi.fn();
  api.get.mockImplementation((url) => Promise.resolve({ data: url === '/api/settings/currencies' ? currencies : profile }));
});

async function open() {
  render(<Settings />);
  await screen.findByDisplayValue('Dlamini');
}

describe('Appearance and currency', () => {
  it('saves the theme at once, keeping the other settings', async () => {
    await open();
    await userEvent.click(screen.getByLabelText('Dark'));
    await waitFor(() => expect(save).toHaveBeenCalledWith({ theme: 'DARK', currency: 'ZAR', notificationsEnabled: true }));
    expect(await screen.findByText('Saved')).toBeTruthy();
  });

  it('changes the currency from the list loaded from the API', async () => {
    await open();
    const select = await screen.findByLabelText('Currency');
    await screen.findByText('USD - US Dollar');
    await userEvent.selectOptions(select, 'USD');
    await waitFor(() => expect(save).toHaveBeenCalledWith({ theme: 'LIGHT', currency: 'USD', notificationsEnabled: true }));
  });

  it('turns notifications off', async () => {
    await open();
    await userEvent.click(screen.getByLabelText(/In-app notifications/));
    await waitFor(() => expect(save).toHaveBeenCalledWith({ theme: 'LIGHT', currency: 'ZAR', notificationsEnabled: false }));
  });

  it('shows an error when saving fails', async () => {
    save.mockRejectedValue({ response: { data: { message: "'QQQ' is not a recognised currency code." } } });
    await open();
    await userEvent.click(screen.getByLabelText('Dark'));
    expect(await screen.findByText(/not a recognised currency/)).toBeTruthy();
  });
});

describe('Profile', () => {
  it('sends empty phone and income as null and updates the header name', async () => {
    api.put.mockResolvedValue({ data: { ...profile, firstName: 'Amahle', lastName: 'Dlamini-Zulu' } });
    await open();
    const last = screen.getByDisplayValue('Dlamini');
    await userEvent.clear(last);
    await userEvent.type(last, 'Dlamini-Zulu');
    await userEvent.click(screen.getByText('Save profile'));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/users/me',
      { firstName: 'Amahle', lastName: 'Dlamini-Zulu', phone: null, monthlyIncome: null }));
    expect(updateUser).toHaveBeenCalledWith({ firstName: 'Amahle', lastName: 'Dlamini-Zulu' });
    expect(await screen.findByText('Profile saved.')).toBeTruthy();
  });

  it('sends the monthly income as a number', async () => {
    api.put.mockResolvedValue({ data: profile });
    await open();
    await userEvent.type(screen.getByLabelText('Monthly income'), '18000');
    await userEvent.click(screen.getByText('Save profile'));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/users/me', expect.objectContaining({ monthlyIncome: 18000 })));
  });
});

describe('Change password', () => {
  const fill = async (current, next, confirm) => {
    await userEvent.type(screen.getByLabelText('Current password'), current);
    await userEvent.type(screen.getByLabelText('New password'), next);
    await userEvent.type(screen.getByLabelText('Confirm new password'), confirm);
    await userEvent.click(screen.getByText('Change password', { selector: 'button' }));
  };

  it('does not call the API when the confirmation differs', async () => {
    await open();
    await fill('OldPass123!', 'NewPass456!', 'Different789!');
    expect(await screen.findByText('The new passwords do not match.')).toBeTruthy();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('changes the password, then logs in again quietly so the session survives', async () => {
    api.post.mockResolvedValue({ data: { message: 'ok' } });
    login.mockResolvedValue();
    await open();
    await fill('OldPass123!', 'NewPass456!', 'NewPass456!');
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/auth/change-password',
      { currentPassword: 'OldPass123!', newPassword: 'NewPass456!' }));
    await waitFor(() => expect(login).toHaveBeenCalledWith('amahle@savewise.test', 'NewPass456!'));
    expect(await screen.findByText(/Password changed/)).toBeTruthy();
    expect(screen.getByLabelText('Current password').value).toBe('');
  });

  it('shows the backend message for a wrong current password', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'Current password is incorrect.' } } });
    await open();
    await fill('WrongOne123', 'NewPass456!', 'NewPass456!');
    expect(await screen.findByText('Current password is incorrect.')).toBeTruthy();
    expect(login).not.toHaveBeenCalled();
  });
});

describe('Profile picture', () => {
  it('uploads a valid image as multipart form data', async () => {
    api.post.mockResolvedValue({ data: { ...profile, hasProfilePicture: true } });
    await open();
    const file = new File(['x'], 'me.png', { type: 'image/png' });
    await userEvent.upload(screen.getByLabelText('Choose picture'), file);
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
    const [url, body] = api.post.mock.calls[0];
    expect(url).toBe('/api/users/me/picture');
    expect(body.get('file').name).toBe('me.png');
  });

  it('rejects the wrong type and oversized files before uploading', async () => {
    const user = userEvent.setup({ applyAccept: false });
    await open();
    await user.upload(screen.getByLabelText('Choose picture'), new File(['x'], 'notes.txt', { type: 'text/plain' }));
    expect(await screen.findByText('Please choose a JPEG, PNG or WebP image.')).toBeTruthy();

    const big = new File(['x'], 'big.png', { type: 'image/png' });
    Object.defineProperty(big, 'size', { value: 3 * 1024 * 1024 });
    await user.upload(screen.getByLabelText('Choose picture'), big);
    expect(await screen.findByText(/over 2 MB/)).toBeTruthy();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('shows the stored picture and can remove it', async () => {
    api.get.mockImplementation((url) => Promise.resolve({
      data: url === '/api/settings/currencies' ? currencies : url === '/api/users/me' ? { ...profile, hasProfilePicture: true } : new Blob(['img']),
    }));
    api.delete.mockResolvedValue({});
    await open();
    expect(await screen.findByAltText('Your profile')).toBeTruthy();
    await userEvent.click(screen.getByText('Remove'));
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/api/users/me/picture'));
    expect(await screen.findByLabelText('No picture')).toBeTruthy();
  });
});
