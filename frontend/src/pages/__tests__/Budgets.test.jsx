import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/client.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  errorMessage: (e) => e.response?.data?.message || 'failed',
  storage: {},
}));
vi.mock('../../context/SettingsContext.jsx', () => ({ useSettings: () => ({ money: (v) => `R${v}` }) }));

import api from '../../api/client.js';
import Budgets from '../Budgets.jsx';

const pad = (n) => String(n).padStart(2, '0');
const now = new Date();
const currentStart = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-01`;
const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1);
const prevStart = `${prev.getFullYear()}-${pad(prev.getMonth() + 1)}-01`;

const food = { id: 1, categoryId: 5, categoryName: 'Food', budgetMonth: currentStart, budgetAmount: 1000, spent: 850, remaining: 150, percentUsed: 85, status: 'WARNING' };
const rent = { id: 9, categoryId: 7, categoryName: 'Rent', budgetMonth: prevStart, budgetAmount: 6500, spent: 6500, remaining: 0, percentUsed: 100, status: 'CRITICAL' };

beforeEach(() => {
  vi.clearAllMocks();
  api.get.mockImplementation((url, opts) => {
    if (url === '/api/categories') return Promise.resolve({ data: [{ id: 5, name: 'Food' }, { id: 6, name: 'Transport' }, { id: 7, name: 'Rent' }] });
    return Promise.resolve({ data: opts.params.month === currentStart ? [food] : [rent] });
  });
});

describe('Budgets', () => {
  it('shows each budget with its status, percentage and what is left', async () => {
    render(<Budgets />);
    expect(await screen.findByText('Near limit')).toBeTruthy();
    expect(screen.getByText('85%')).toBeTruthy();
    expect(screen.getByText('R150 left')).toBeTruthy();
    expect(screen.getByText('R850 of R1000')).toBeTruthy();
  });

  it('only offers categories without a budget, and posts the first day of the month', async () => {
    api.post.mockResolvedValue({ data: {} });
    render(<Budgets />);
    await screen.findByText('Near limit');
    await userEvent.click(screen.getByText('+ Add budget'));
    const dialog = screen.getByRole('dialog');
    const select = within(dialog).getByRole('combobox');
    expect(within(select).queryByText('Food')).toBeNull();       // already budgeted this month
    await userEvent.selectOptions(select, '6');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '500');
    await userEvent.click(within(dialog).getByText('Save'));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/budgets', { categoryId: 6, budgetMonth: currentStart, budgetAmount: 500 }));
  });

  it('shows the backend error when saving fails', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'You already have a budget for this category in that month.' } } });
    render(<Budgets />);
    await screen.findByText('Near limit');
    await userEvent.click(screen.getByText('+ Add budget'));
    const dialog = screen.getByRole('dialog');
    await userEvent.selectOptions(within(dialog).getByRole('combobox'), '6');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '100');
    await userEvent.click(within(dialog).getByText('Save'));
    expect(await within(dialog).findByText(/already have a budget/)).toBeTruthy();
  });

  it('copies only the missing budgets from last month', async () => {
    api.post.mockResolvedValue({ data: {} });
    render(<Budgets />);
    await screen.findByText('Near limit');
    await userEvent.click(screen.getByText('Copy last month'));
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
    expect(api.post).toHaveBeenCalledWith('/api/budgets', { categoryId: 7, budgetMonth: currentStart, budgetAmount: 6500 });
    expect(await screen.findByText('Copied 1 budget from last month.')).toBeTruthy();
  });

  it('deletes only after confirmation', async () => {
    api.delete.mockResolvedValue({});
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    render(<Budgets />);
    await screen.findByText('Near limit');
    await userEvent.click(screen.getByText('Delete'));
    expect(api.delete).not.toHaveBeenCalled();
    await userEvent.click(screen.getByText('Delete'));
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/api/budgets/1'));
    confirm.mockRestore();
  });
});
