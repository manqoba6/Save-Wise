import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/client.js', () => ({
  default: { get: vi.fn() },
  errorMessage: (e) => e.response?.data?.message || 'failed',
  storage: {},
}));
vi.mock('../../context/SettingsContext.jsx', () => ({ useSettings: () => ({ money: (v) => `R${v}` }) }));
vi.mock('react-chartjs-2', () => ({ Bar: () => <div data-testid="bar-chart" />, Doughnut: () => <div data-testid="doughnut" /> }));
vi.mock('../../utils.js', async (original) => ({ ...(await original()), downloadBlob: vi.fn() }));

import api from '../../api/client.js';
import { downloadBlob } from '../../utils.js';
import Reports from '../Reports.jsx';

const report = {
  type: 'MONTHLY', label: 'October 2026', from: '2026-10-01', to: '2026-10-31', currency: 'ZAR',
  summary: { totalIncome: 15000, totalExpenses: 9000, net: 6000, savingsRatePercent: 40.0, savingsDeposits: 1500, savingsWithdrawals: 0, transactionCount: 2 },
  incomeByCategory: [{ categoryName: 'Salary', amount: 15000, percentOfTotal: 100.0 }],
  expensesByCategory: [{ categoryName: 'Food', amount: 9000, percentOfTotal: 100.0 }],
  breakdown: [{ label: '1 Oct', income: 15000, expenses: 0, net: 15000 }, { label: '2 Oct', income: 0, expenses: 9000, net: -9000 }],
  budgets: [{ id: 1, categoryName: 'Food', budgetAmount: 8000, spent: 9000, percentUsed: 112.5, status: 'CRITICAL' }],
  transactions: [
    { date: '2026-10-01', type: 'INCOME', categoryName: 'Salary', description: 'October salary', amount: 15000 },
    { date: '2026-10-02', type: 'EXPENSE', categoryName: 'Food', description: null, amount: 9000 },
  ],
};
const lastReportCall = () => api.get.mock.calls.filter(([u]) => u === '/api/reports').at(-1)[1].params;

beforeEach(() => {
  vi.clearAllMocks();
  api.get.mockImplementation((url) => Promise.resolve(url === '/api/reports' ? { data: report } : { data: new Blob(['x']), headers: { 'content-disposition': 'attachment; filename="savewise-monthly-2026-10-01.pdf"' } }));
});

describe('Reports', () => {
  it('loads the monthly report by default and shows the summary, categories, budgets and transactions', async () => {
    render(<Reports />);
    expect(await screen.findByText(/October 2026/)).toBeTruthy();
    expect(screen.getByText('Income', { selector: '.stat > span' }).nextSibling.textContent).toBe('R15000');
    expect(screen.getByText('Net', { selector: '.stat > span' }).nextSibling.textContent).toBe('R6000');
    expect(screen.getByText('40%')).toBeTruthy();
    expect(screen.getByText('Over budget')).toBeTruthy();
    expect(screen.getByText('October salary')).toBeTruthy();
    expect(screen.getByText('+R15000')).toBeTruthy();
    expect(screen.getByText('-R9000')).toBeTruthy();
    expect(screen.getByTestId('bar-chart')).toBeTruthy();
    expect(lastReportCall()).toMatchObject({ type: 'MONTHLY', date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/) });
  });

  it('switching the type asks for that report and shows the matching date control', async () => {
    render(<Reports />);
    await screen.findByText(/October 2026/);

    await userEvent.click(screen.getByRole('button', { name: 'Weekly' }));
    await waitFor(() => expect(lastReportCall().type).toBe('WEEKLY'));
    expect(screen.getByLabelText('Date')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Annual' }));
    await waitFor(() => expect(lastReportCall().type).toBe('ANNUAL'));
    expect(screen.getByLabelText('Year')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: 'Daily' }));
    await waitFor(() => expect(lastReportCall().type).toBe('DAILY'));
  });

  it('picking a different month sends the first of that month', async () => {
    render(<Reports />);
    await screen.findByText(/October 2026/);
    // month inputs cannot be typed into character by character, so change the value in one go
    fireEvent.change(screen.getByLabelText('Month'), { target: { value: '2026-08' } });
    await waitFor(() => expect(lastReportCall()).toMatchObject({ type: 'MONTHLY', date: '2026-08-01' }));
  });

  it('downloads the PDF using the file name the server chose', async () => {
    render(<Reports />);
    await screen.findByText(/October 2026/);
    await userEvent.click(screen.getByText('Download PDF'));
    await waitFor(() => expect(downloadBlob).toHaveBeenCalledTimes(1));
    expect(api.get).toHaveBeenCalledWith('/api/reports/export', { params: expect.objectContaining({ type: 'MONTHLY', format: 'pdf' }), responseType: 'blob' });
    expect(downloadBlob.mock.calls[0][1]).toBe('savewise-monthly-2026-10-01.pdf');
  });

  it('downloads the CSV, falling back to a sensible name when the header is missing', async () => {
    api.get.mockImplementation((url) => Promise.resolve(url === '/api/reports' ? { data: report } : { data: new Blob(['x']), headers: {} }));
    render(<Reports />);
    await screen.findByText(/October 2026/);
    await userEvent.click(screen.getByText('Download CSV'));
    await waitFor(() => expect(downloadBlob).toHaveBeenCalled());
    expect(downloadBlob.mock.calls[0][1]).toBe('savewise-report.csv');
  });

  it('reads the server message out of a failed download', async () => {
    api.get.mockImplementation((url) => (url === '/api/reports' ? Promise.resolve({ data: report })
      : Promise.reject({ response: { data: { text: () => Promise.resolve(JSON.stringify({ message: 'Format must be pdf or csv.' })) } } })));
    render(<Reports />);
    await screen.findByText(/October 2026/);
    await userEvent.click(screen.getByText('Download PDF'));
    expect(await screen.findByText('Format must be pdf or csv.')).toBeTruthy();
    expect(downloadBlob).not.toHaveBeenCalled();
  });

  it('shows empty states and hides the charts for a period with nothing in it', async () => {
    api.get.mockResolvedValue({ data: { ...report, summary: { ...report.summary, totalIncome: 0, totalExpenses: 0, net: 0, savingsRatePercent: null, transactionCount: 0 },
      incomeByCategory: [], expensesByCategory: [], breakdown: [], budgets: [], transactions: [] } });
    render(<Reports />);
    expect(await screen.findByText('No transactions in this period.')).toBeTruthy();
    expect(screen.getByText('n/a')).toBeTruthy();
    expect(screen.queryByTestId('bar-chart')).toBeNull();
    expect(screen.queryByTestId('doughnut')).toBeNull();
  });

  it('shows only the first 100 transactions and points to the CSV for the rest', async () => {
    const many = Array.from({ length: 130 }, (_, i) => ({ date: '2026-10-01', type: 'EXPENSE', categoryName: 'Food', description: `item ${i}`, amount: 10 }));
    api.get.mockResolvedValue({ data: { ...report, transactions: many } });
    render(<Reports />);
    expect(await screen.findByText(/Showing the first 100 of 130/)).toBeTruthy();
    expect(screen.getByText('item 99')).toBeTruthy();
    expect(screen.queryByText('item 100')).toBeNull();
  });

  it('shows an error when the report cannot be loaded', async () => {
    api.get.mockRejectedValue({ response: { data: { message: 'Report type must be DAILY, WEEKLY, MONTHLY or ANNUAL.' } } });
    render(<Reports />);
    expect(await screen.findByText(/Report type must be/)).toBeTruthy();
  });
});
