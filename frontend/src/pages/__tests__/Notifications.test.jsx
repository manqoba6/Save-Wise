import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const refresh = vi.fn();
vi.mock('../../api/client.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  errorMessage: (e) => e.response?.data?.message || 'failed',
  storage: {},
}));
vi.mock('../../context/NotificationsContext.jsx', () => ({ useNotifications: () => ({ unread: 1, refresh }) }));

import api from '../../api/client.js';
import Notifications from '../Notifications.jsx';

const page = (content) => ({ data: { content, page: 0, size: 15, totalElements: content.length, totalPages: 1 } });
const unread = { id: 3, title: 'Budget warning', message: 'You have used 80% of your Food budget.', type: 'BUDGET_WARNING', read: false, createdAt: '2026-11-05T08:50:12' };
const read = { id: 2, title: 'Goal achieved', message: 'You reached your goal.', type: 'GOAL_ACHIEVED', read: true, createdAt: '2026-11-04T10:00:00' };

beforeEach(() => { vi.clearAllMocks(); api.get.mockResolvedValue(page([unread, read])); });

describe('Notifications', () => {
  it('lists notifications and offers Mark read only on unread ones', async () => {
    render(<Notifications />);
    expect(await screen.findByText('Budget warning')).toBeTruthy();
    expect(screen.getByText('Goal achieved')).toBeTruthy();
    expect(screen.getAllByText('Mark read')).toHaveLength(1);
  });

  it('marks one as read and refreshes the unread badge', async () => {
    api.put.mockResolvedValue({ data: {} });
    render(<Notifications />);
    await screen.findByText('Budget warning');
    await userEvent.click(screen.getByText('Mark read'));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/notifications/3/read'));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });

  it('marks everything as read', async () => {
    api.put.mockResolvedValue({ data: {} });
    render(<Notifications />);
    await screen.findByText('Budget warning');
    await userEvent.click(screen.getByText('Mark all as read'));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/notifications/read-all'));
  });

  it('asks for unread only when the box is ticked', async () => {
    render(<Notifications />);
    await screen.findByText('Budget warning');
    await userEvent.click(screen.getByLabelText(/Unread only/));
    await waitFor(() => {
      const last = api.get.mock.calls.at(-1);
      expect(last[1].params).toMatchObject({ unreadOnly: true, page: 0 });
    });
  });

  it('shows a friendly empty state', async () => {
    api.get.mockResolvedValue(page([]));
    render(<Notifications />);
    expect(await screen.findByText(/No notifications yet/)).toBeTruthy();
  });
});
