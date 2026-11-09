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
import TransactionsPage from '../TransactionsPage.jsx';

const page = (content) => ({ data: { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 } });
const groceries = { id: 7, categoryId: 5, categoryName: 'Food', amount: 250.5, paymentMethod: 'CARD', description: 'Groceries', expenseDate: '2026-11-03' };

beforeEach(() => {
  vi.clearAllMocks();
  api.get.mockImplementation((url) => {
    if (url === '/api/categories') return Promise.resolve({ data: [{ id: 5, name: 'Food' }, { id: 6, name: 'Transport' }] });
    return Promise.resolve(page([groceries]));
  });
});

describe('TransactionsPage (expenses)', () => {
  it('lists records with a minus sign and the money format', async () => {
    render(<TransactionsPage kind="expense" />);
    expect(await screen.findByText('Groceries')).toBeTruthy();
    expect(screen.getByText('-R250.5')).toBeTruthy();
    expect(screen.getByText('CARD')).toBeTruthy();
  });

  it('shows the empty message when there is nothing', async () => {
    api.get.mockImplementation((url) => Promise.resolve(url === '/api/categories' ? { data: [] } : page([])));
    render(<TransactionsPage kind="expense" />);
    expect(await screen.findByText('No expenses recorded yet.')).toBeTruthy();
  });

  it('searches after the user stops typing, sending q to the API', async () => {
    render(<TransactionsPage kind="expense" />);
    await screen.findByText('Groceries');
    await userEvent.type(screen.getByPlaceholderText('Search descriptions...'), 'groc');
    await waitFor(() => {
      const last = api.get.mock.calls.filter(([u]) => u === '/api/expenses').at(-1);
      expect(last[1].params).toMatchObject({ q: 'groc', page: 0 });
    });
  });

  it('posts a new expense with the right body and refreshes the list', async () => {
    api.post.mockResolvedValue({ data: {} });
    render(<TransactionsPage kind="expense" />);
    await screen.findByText('Groceries');
    const listCalls = () => api.get.mock.calls.filter(([u]) => u === '/api/expenses').length;
    const before = listCalls();

    await userEvent.click(screen.getByText('+ Add expense'));
    const dialog = screen.getByRole('dialog');
    await userEvent.selectOptions(within(dialog).getAllByRole('combobox')[0], '5');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '100');
    await userEvent.click(within(dialog).getByText('Save'));

    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
    const [url, body] = api.post.mock.calls[0];
    expect(url).toBe('/api/expenses');
    expect(body).toMatchObject({ categoryId: 5, amount: 100, description: null, paymentMethod: null });
    expect(body.expenseDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    await waitFor(() => expect(listCalls()).toBeGreaterThan(before));
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('shows the backend error message inside the form', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'Category must be of type EXPENSE.' } } });
    render(<TransactionsPage kind="expense" />);
    await screen.findByText('Groceries');
    await userEvent.click(screen.getByText('+ Add expense'));
    const dialog = screen.getByRole('dialog');
    await userEvent.selectOptions(within(dialog).getAllByRole('combobox')[0], '5');
    await userEvent.type(within(dialog).getByRole('spinbutton'), '10');
    await userEvent.click(within(dialog).getByText('Save'));
    expect(await within(dialog).findByText('Category must be of type EXPENSE.')).toBeTruthy();
  });

  it('edits with PUT to the record id', async () => {
    api.put.mockResolvedValue({ data: {} });
    render(<TransactionsPage kind="expense" />);
    await screen.findByText('Groceries');
    await userEvent.click(screen.getByText('Edit'));
    const dialog = screen.getByRole('dialog');
    const amount = within(dialog).getByRole('spinbutton');
    await userEvent.clear(amount);
    await userEvent.type(amount, '300');
    await userEvent.click(within(dialog).getByText('Save'));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/expenses/7', expect.objectContaining({ amount: 300, categoryId: 5 })));
  });

  it('deletes only after confirmation', async () => {
    api.delete.mockResolvedValue({});
    const confirm = vi.spyOn(window, 'confirm');
    render(<TransactionsPage kind="expense" />);
    await screen.findByText('Groceries');

    confirm.mockReturnValueOnce(false);
    await userEvent.click(screen.getByText('Delete'));
    expect(api.delete).not.toHaveBeenCalled();

    confirm.mockReturnValueOnce(true);
    await userEvent.click(screen.getByText('Delete'));
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/api/expenses/7'));
  });
});

describe('TransactionsPage (income)', () => {
  it('uses the income endpoint, a plus sign and no payment column', async () => {
    api.get.mockImplementation((url) => {
      if (url === '/api/categories') return Promise.resolve({ data: [{ id: 1, name: 'Salary' }] });
      return Promise.resolve(page([{ id: 2, categoryId: 1, categoryName: 'Salary', amount: 15000, description: 'October', incomeDate: '2026-10-25' }]));
    });
    render(<TransactionsPage kind="income" />);
    expect(await screen.findByText('+R15000')).toBeTruthy();
    expect(screen.queryByText('Paid by')).toBeNull();
    expect(api.get).toHaveBeenCalledWith('/api/categories', { params: { type: 'INCOME' } });
  });
});
