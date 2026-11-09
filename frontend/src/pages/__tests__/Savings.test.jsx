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
import Savings from '../Savings.jsx';

const emergency = { id: 1, accountName: 'Emergency Fund', currentBalance: 1500, targetAmount: 20000, createdAt: '2026-10-01T10:00:00' };
const vacation = { id: 2, accountName: 'Vacation', currentBalance: 0, targetAmount: null, createdAt: '2026-10-02T10:00:00' };
const history = (rows) => ({ data: { content: rows, page: 0, size: 10, totalElements: rows.length, totalPages: 1 } });
const deposit = { id: 11, accountId: 1, accountName: 'Emergency Fund', transactionType: 'DEPOSIT', amount: 1500, transactionDate: '2026-11-01', description: 'October top-up' };

function setup(accounts = [emergency, vacation]) {
  api.get.mockImplementation((url) => Promise.resolve(url === '/api/savings/accounts' ? { data: accounts } : history([deposit])));
}
beforeEach(() => { vi.clearAllMocks(); setup(); });

describe('Savings', () => {
  it('shows accounts, the total, target progress and history', async () => {
    render(<Savings />);
    expect(await screen.findByText('Emergency Fund', { selector: 'strong' })).toBeTruthy();
    expect(screen.getByText('Total saved').nextSibling.textContent).toBe('R1500');
    expect(screen.getByText('8% of R20000 target')).toBeTruthy();
    expect(await screen.findByText('October top-up')).toBeTruthy();
    expect(screen.getByText('+R1500')).toBeTruthy();
  });

  it('deposits into the chosen account', async () => {
    api.post.mockResolvedValue({ data: {} });
    render(<Savings />);
    await screen.findByText('October top-up');
    await userEvent.click(screen.getAllByText('Deposit')[0]);
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '250');
    await userEvent.click(within(dialog).getByText('Save'));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/savings/deposit',
      expect.objectContaining({ accountId: 1, amount: 250, description: null })));
  });

  it('shows the insufficient-funds message from a withdrawal', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'Insufficient savings balance. Savings cannot go negative.' } } });
    render(<Savings />);
    await screen.findByText('October top-up');
    await userEvent.click(screen.getAllByText('Withdraw')[0]);
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '99999');
    await userEvent.click(within(dialog).getByText('Save'));
    expect(await within(dialog).findByText(/Insufficient savings balance/)).toBeTruthy();
  });

  it('transfers to a different account and never offers the source account', async () => {
    api.post.mockResolvedValue({ data: {} });
    render(<Savings />);
    await screen.findByText('October top-up');
    await userEvent.click(screen.getAllByText('Transfer')[0]);
    const dialog = screen.getByRole('dialog');
    const select = within(dialog).getByRole('combobox');
    expect(within(select).queryByText('Emergency Fund')).toBeNull();
    await userEvent.selectOptions(select, '2');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '500');
    await userEvent.click(within(dialog).getByText('Save'));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/savings/transfer',
      expect.objectContaining({ fromAccountId: 1, toAccountId: 2, amount: 500 })));
  });

  it('hides Transfer when there is only one account', async () => {
    setup([emergency]);
    render(<Savings />);
    await screen.findByText('October top-up');
    expect(screen.queryByText('Transfer')).toBeNull();
  });

  it('creates an account with a null target when none is given', async () => {
    api.post.mockResolvedValue({ data: {} });
    render(<Savings />);
    await screen.findByText('October top-up');
    await userEvent.click(screen.getByText('+ New account'));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByPlaceholderText('e.g. Emergency fund'), 'Car fund');
    await userEvent.click(within(dialog).getByText('Save'));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/savings/accounts', { accountName: 'Car fund', targetAmount: null }));
  });
});
