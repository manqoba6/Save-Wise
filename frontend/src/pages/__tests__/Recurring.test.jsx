import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/client.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  errorMessage: (e) => e.response?.data?.message || 'failed',
  storage: {},
}));
vi.mock('../../context/SettingsContext.jsx', () => ({ useSettings: () => ({ money: (v) => `R${Math.round(v * 100) / 100}` }) }));

import api from '../../api/client.js';
import Recurring from '../Recurring.jsx';

const categories = [
  { id: 1, name: 'Salary', type: 'INCOME' }, { id: 5, name: 'Food', type: 'EXPENSE' }, { id: 7, name: 'Rent', type: 'EXPENSE' },
];
const rent = { id: 1, transactionType: 'EXPENSE', categoryId: 7, categoryName: 'Rent', amount: 6500, frequency: 'MONTHLY', startDate: '2026-11-01', endDate: null, nextExecution: '2026-12-01', description: 'Rent', active: true, status: 'ACTIVE' };
const salary = { id: 2, transactionType: 'INCOME', categoryId: 1, categoryName: 'Salary', amount: 15000, frequency: 'MONTHLY', startDate: '2026-10-30', endDate: '2027-06-30', nextExecution: '2026-11-30', description: 'Salary', active: false, status: 'PAUSED' };
const weekly = { id: 3, transactionType: 'EXPENSE', categoryId: 5, categoryName: 'Food', amount: 300, frequency: 'WEEKLY', startDate: '2026-11-02', endDate: null, nextExecution: '2026-11-09', description: null, active: true, status: 'ACTIVE' };
let list;

beforeEach(() => {
  vi.clearAllMocks();
  list = [rent, salary, weekly];
  api.get.mockImplementation((url, opts) => {
    if (url === '/api/categories') return Promise.resolve({ data: categories });
    if (url === '/api/recurring-transactions') return Promise.resolve({ data: list });
    if (url.endsWith('/history')) return Promise.resolve({ data: { content: [{ recordId: 90, date: '2026-11-01', amount: 6500, description: 'Rent' }], page: opts.params.page, size: 10, totalElements: 1, totalPages: 1 } });
    return Promise.reject(new Error(`unexpected ${url}`));
  });
});

describe('Recurring', () => {
  it('lists schedules with sign, frequency, status and next date', async () => {
    render(<Recurring />);
    expect(await screen.findByText('-R6500')).toBeTruthy();
    expect(screen.getByText('+R15000')).toBeTruthy();
    expect(screen.getByText(/Next: 2026-12-01/)).toBeTruthy();
    expect(screen.getAllByText('Paused').length).toBeGreaterThan(0);
    expect(screen.getByText(/nothing will post until you resume/)).toBeTruthy();
    expect(screen.getByText(/Every week/)).toBeTruthy();
  });

  it('adds up active schedules into an approximate monthly figure (paused ones are left out)', async () => {
    render(<Recurring />);
    await screen.findByText('-R6500');
    // 6500 monthly + 300 weekly * 52/12 = 6500 + 1300 = 7800; the paused salary is excluded
    expect(screen.getByText('Expenses a month (approx.)').nextSibling.textContent).toBe('R7800');
    expect(screen.queryByText('Income a month (approx.)')?.nextSibling.textContent).toBe('R0');
  });

  it('filters by status tab', async () => {
    render(<Recurring />);
    await screen.findByText('-R6500');
    await userEvent.click(screen.getByRole('button', { name: 'Paused' }));
    expect(screen.queryByText('-R6500')).toBeNull();
    expect(screen.getByText('+R15000')).toBeTruthy();
  });

  it('creates a schedule; switching to Income clears the category and offers income categories only', async () => {
    api.post.mockResolvedValue({ data: {} });
    render(<Recurring />);
    await screen.findByText('-R6500');
    await userEvent.click(screen.getByText('+ New recurring'));
    const dialog = screen.getByRole('dialog');
    const category = within(dialog).getAllByRole('combobox')[0];
    expect(within(category).getByText('Food')).toBeTruthy();
    expect(within(category).queryByText('Salary')).toBeNull();

    await userEvent.click(within(dialog).getByLabelText('Income'));
    expect(within(category).queryByText('Food')).toBeNull();
    await userEvent.selectOptions(category, '1');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '15000');
    await userEvent.click(within(dialog).getByText('Save'));

    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
    const [url, body] = api.post.mock.calls[0];
    expect(url).toBe('/api/recurring-transactions');
    expect(body).toMatchObject({ transactionType: 'INCOME', categoryId: 1, amount: 15000, frequency: 'MONTHLY', endDate: null, description: null });
    expect(body.startDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('shows the backend message when the start date is rejected', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'Start date cannot be in the past.' } } });
    render(<Recurring />);
    await screen.findByText('-R6500');
    await userEvent.click(screen.getByText('+ New recurring'));
    const dialog = screen.getByRole('dialog');
    await userEvent.selectOptions(within(dialog).getAllByRole('combobox')[0], '5');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '100');
    await userEvent.click(within(dialog).getByText('Save'));
    expect(await within(dialog).findByText('Start date cannot be in the past.')).toBeTruthy();
  });

  it('edits with PUT to the schedule id', async () => {
    api.put.mockResolvedValue({ data: {} });
    render(<Recurring />);
    await screen.findByText('-R6500');
    await userEvent.click(screen.getAllByText('Edit')[0]);
    const dialog = screen.getByRole('dialog');
    const amount = within(dialog).getByRole('spinbutton');
    await userEvent.clear(amount);
    await userEvent.type(amount, '7000');
    await userEvent.click(within(dialog).getByText('Save'));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/recurring-transactions/1', expect.objectContaining({ amount: 7000, categoryId: 7, transactionType: 'EXPENSE' })));
  });

  it('pauses an active schedule and resumes a paused one', async () => {
    api.put.mockResolvedValue({ data: {} });
    render(<Recurring />);
    await screen.findByText('-R6500');
    await userEvent.click(screen.getAllByText('Pause')[0]);
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/recurring-transactions/1/pause'));
    await userEvent.click(screen.getByText('Resume'));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/recurring-transactions/2/resume'));
  });

  it('shows the message when a finished schedule cannot be resumed', async () => {
    api.put.mockRejectedValue({ response: { data: { message: 'This schedule has ended. Extend its end date first.' } } });
    render(<Recurring />);
    await screen.findByText('-R6500');
    await userEvent.click(screen.getByText('Resume'));
    expect(await screen.findByText(/has ended/)).toBeTruthy();
  });

  it('deletes only after confirmation', async () => {
    api.delete.mockResolvedValue({});
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    render(<Recurring />);
    await screen.findByText('-R6500');
    await userEvent.click(screen.getAllByText('Delete')[0]);
    expect(api.delete).not.toHaveBeenCalled();
    await userEvent.click(screen.getAllByText('Delete')[0]);
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/api/recurring-transactions/1'));
    confirm.mockRestore();
  });

  it('shows what a schedule has posted so far', async () => {
    render(<Recurring />);
    await screen.findByText('-R6500');
    await userEvent.click(screen.getAllByText('History')[0]);
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('2026-11-01')).toBeTruthy();
    expect(within(dialog).getByText('-R6500')).toBeTruthy();
    expect(api.get).toHaveBeenCalledWith('/api/recurring-transactions/1/history', { params: { page: 0, size: 10 } });
  });

  it('shows a friendly empty state', async () => {
    list = [];
    render(<Recurring />);
    expect(await screen.findByText(/No recurring transactions yet/)).toBeTruthy();
  });
});
