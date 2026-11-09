import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const refresh = vi.fn();
vi.mock('../../api/client.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  errorMessage: (e) => e.response?.data?.message || 'failed',
  storage: {},
}));
vi.mock('../../context/SettingsContext.jsx', () => ({ useSettings: () => ({ money: (v) => `R${v}` }) }));
vi.mock('../../context/NotificationsContext.jsx', () => ({ useNotifications: () => ({ unread: 0, refresh }) }));

import api from '../../api/client.js';
import Goals from '../Goals.jsx';

const laptop = { id: 1, title: 'New laptop', targetAmount: 12000, currentAmount: 5000, deadline: null, status: 'ACTIVE', percentComplete: 41.7 };

beforeEach(() => { vi.clearAllMocks(); api.get.mockResolvedValue({ data: [laptop] }); });

describe('Goals', () => {
  it('asks the API for active goals by default and shows progress', async () => {
    render(<Goals />);
    expect(await screen.findByText('New laptop')).toBeTruthy();
    expect(screen.getByText('R5000 of R12000')).toBeTruthy();
    expect(screen.getByText('41.7%')).toBeTruthy();
    expect(api.get).toHaveBeenCalledWith('/api/goals', { params: { status: 'ACTIVE' } });
  });

  it('switches tabs and asks for that status; All sends no filter', async () => {
    render(<Goals />);
    await screen.findByText('New laptop');
    await userEvent.click(screen.getByText('Completed'));
    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith('/api/goals', { params: { status: 'COMPLETED' } }));
    await userEvent.click(screen.getByText('All'));
    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith('/api/goals', { params: {} }));
  });

  it('creates a goal; an empty deadline is sent as null', async () => {
    api.post.mockResolvedValue({ data: {} });
    render(<Goals />);
    await screen.findByText('New laptop');
    await userEvent.click(screen.getByText('+ New goal'));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByPlaceholderText('e.g. New laptop'), 'Holiday');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '8000');
    await userEvent.click(within(dialog).getByText('Save'));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/goals', { title: 'Holiday', targetAmount: 8000, deadline: null }));
  });

  it('contributes, and celebrates and refreshes notifications when the goal completes', async () => {
    api.post.mockResolvedValue({ data: { ...laptop, currentAmount: 12000, status: 'COMPLETED' } });
    render(<Goals />);
    await screen.findByText('New laptop');
    await userEvent.click(screen.getByText('Add money'));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '7000');
    await userEvent.click(within(dialog).getByText('Save'));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/goals/1/contribute', { amount: 7000 }));
    expect(await screen.findByText('You reached your goal "New laptop"!')).toBeTruthy();
    expect(refresh).toHaveBeenCalled();
  });

  it('does not celebrate a contribution that leaves the goal active', async () => {
    api.post.mockResolvedValue({ data: { ...laptop, currentAmount: 6000 } });
    render(<Goals />);
    await screen.findByText('New laptop');
    await userEvent.click(screen.getByText('Add money'));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '1000');
    await userEvent.click(within(dialog).getByText('Save'));
    await waitFor(() => expect(api.post).toHaveBeenCalled());
    expect(screen.queryByText(/You reached your goal/)).toBeNull();
    expect(refresh).not.toHaveBeenCalled();
  });

  it('cancels only after confirmation', async () => {
    api.put.mockResolvedValue({ data: {} });
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    render(<Goals />);
    await screen.findByText('New laptop');
    await userEvent.click(screen.getByText('Cancel goal'));
    expect(api.put).not.toHaveBeenCalled();
    await userEvent.click(screen.getByText('Cancel goal'));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/goals/1/cancel'));
    confirm.mockRestore();
  });
});
