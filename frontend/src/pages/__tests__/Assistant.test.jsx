import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/client.js', () => ({
  default: { get: vi.fn() },
  errorMessage: (e) => e.response?.data?.message || 'failed',
  storage: {},
}));
vi.mock('../../context/SettingsContext.jsx', () => ({ useSettings: () => ({ money: (v) => `R${v}` }) }));
// Chart.js needs a real canvas, which jsdom does not have
vi.mock('react-chartjs-2', () => ({ Bar: () => <div data-testid="bar-chart" />, Line: () => <div data-testid="line-chart" /> }));

import api from '../../api/client.js';
import Assistant from '../Assistant.jsx';

const health = { score: 72, label: 'GOOD', components: [{ name: 'Savings rate', score: 16, maxScore: 20 }, { name: 'Budget adherence', score: 10, maxScore: 20 }] };
const advice = {
  financialHealth: health,
  insights: [
    { type: 'BUDGET_EXCEEDED', severity: 'CRITICAL', title: 'Over budget: Food', message: 'You have spent R1050 of your R1000 Food budget (105.0%).', categoryName: 'Food', amount: 50 },
    { type: 'SET_GOAL', severity: 'INFO', title: 'Give your savings a purpose', message: 'Set a goal.', categoryName: null, amount: 500 },
  ],
};
const overspending = { income: 15000, expenses: 9000, findings: [{ type: 'SPENDING_SPIKE', severity: 'WARNING', title: 'Shopping spending is unusually high', message: 'Shopping is 80% above your usual.', categoryName: 'Shopping', amount: 400 }] };
const saving = { monthsAnalysed: 3, averageMonthlyIncome: 15000, averageMonthlyExpenses: 12000, averageMonthlySurplus: 3000, averageSavingsRatePercent: 20.0, recommendations: [{ type: 'EMERGENCY_FUND', severity: 'INFO', title: 'Build up your emergency fund', message: 'Your savings cover about 1.0 months.', categoryName: null, amount: 800 }] };
const trends = {
  monthsAnalysed: 6, message: null, months: [{ month: '2026-09', income: 15000, expenses: 11000, net: 4000 }, { month: '2026-10', income: 15000, expenses: 12000, net: 3000 }],
  incomeTrend: 'STABLE', incomeChangePerMonthPercent: 0.0, expenseTrend: 'RISING', expenseChangePerMonthPercent: 4.2,
  categories: [{ categoryName: 'Food', lastMonth: 3200, previousAverage: 2400, changePercent: 33.3, direction: 'RISING' }, { categoryName: 'Gym', lastMonth: 500, previousAverage: 0, changePercent: null, direction: 'NEW' }],
};
const forecast = {
  enoughData: true, confidence: 'MEDIUM', monthsAnalysed: 4, message: 'Based on only 4 full months of history, so treat these figures as a rough guide.',
  currentBalance: 20000, currentSavings: 2000, averageMonthlyIncome: 15000, averageMonthlyExpenses: 12000, averageMonthlyNet: 3000, averageMonthlySavingsContribution: 500,
  projections: [
    { monthsAhead: 1, month: '2026-12', projectedBalance: 23000, balanceLow: 21000, balanceHigh: 25000, projectedSavings: 2500 },
    { monthsAhead: 12, month: '2027-11', projectedBalance: 56000, balanceLow: 49000, balanceHigh: 63000, projectedSavings: 8000 },
  ],
  goals: [
    { goalId: 1, title: 'New laptop', remaining: 7000, deadline: '2027-03-31', requiredMonthly: 1400, monthsToGoalAtCurrentPace: 3, onTrack: true },
    { goalId: 2, title: 'Car deposit', remaining: 60000, deadline: '2027-01-31', requiredMonthly: 20000, monthsToGoalAtCurrentPace: 20, onTrack: false },
  ],
};

const responses = {
  '/api/assistant/advice': advice, '/api/assistant/overspending': overspending, '/api/assistant/saving-recommendations': saving,
  '/api/assistant/spending-trends': trends, '/api/predictions': forecast,
};
beforeEach(() => {
  vi.clearAllMocks();
  api.get.mockImplementation((url) => Promise.resolve({ data: responses[url] }));
});

describe('Assistant', () => {
  it('opens on Advice with the health score and insights, urgent first', async () => {
    render(<Assistant />);
    expect(await screen.findByText('Over budget: Food')).toBeTruthy();
    expect(screen.getByText('72')).toBeTruthy();
    expect(screen.getByText('16/20')).toBeTruthy();
    const items = screen.getAllByRole('listitem').filter((li) => li.classList.contains('insight'));
    expect(items[0].classList.contains('critical')).toBe(true);
    expect(items[0].textContent).toContain('Urgent');
    expect(items[1].classList.contains('info')).toBe(true);
    expect(api.get).toHaveBeenCalledWith('/api/assistant/advice', { params: { month: expect.stringMatching(/^\d{4}-\d{2}-01$/) } });
  });

  it('shows an error from the API', async () => {
    api.get.mockRejectedValue({ response: { data: { message: 'Something broke.' } } });
    render(<Assistant />);
    expect(await screen.findByText('Something broke.')).toBeTruthy();
  });

  it('Overspending tab loads its own data and the month picker follows it', async () => {
    render(<Assistant />);
    await screen.findByText('Over budget: Food');
    await userEvent.click(screen.getByText('Overspending'));
    expect(await screen.findByText('Shopping spending is unusually high')).toBeTruthy();
    expect(screen.getByText('R15000')).toBeTruthy();
    expect(screen.getByLabelText('Month')).toBeTruthy();
  });

  it('Saving tips shows the averages and recommendations; the month picker is hidden', async () => {
    render(<Assistant />);
    await screen.findByText('Over budget: Food');
    await userEvent.click(screen.getByText('Saving tips'));
    expect(await screen.findByText('Build up your emergency fund')).toBeTruthy();
    expect(screen.getByText('Avg left over / month').nextSibling.textContent).toBe('R3000');
    expect(screen.getByText('20%')).toBeTruthy();
    expect(screen.queryByLabelText('Month')).toBeNull();
  });

  it('Trends shows direction chips, category changes, and re-fetches for a longer window', async () => {
    render(<Assistant />);
    await userEvent.click(await screen.findByText('Trends'));
    expect(await screen.findByText('Rising')).toBeTruthy();
    expect(screen.getByText('+4.2% a month')).toBeTruthy();
    expect(screen.getByText('+33.3%')).toBeTruthy();
    expect(screen.getByText('New')).toBeTruthy();
    expect(screen.getAllByTestId('bar-chart')).toHaveLength(1);

    await userEvent.selectOptions(screen.getByRole('combobox'), '12');
    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith('/api/assistant/spending-trends', { params: { months: 12 } }));
  });

  it('Trends explains when there is not enough history', async () => {
    api.get.mockImplementation((url) => Promise.resolve({ data: url === '/api/assistant/spending-trends'
      ? { monthsAnalysed: 1, months: [], categories: [], incomeTrend: 'INSUFFICIENT_DATA', expenseTrend: 'INSUFFICIENT_DATA', message: 'Trends need at least two full months of history.' }
      : advice }));
    render(<Assistant />);
    await userEvent.click(await screen.findByText('Trends'));
    expect(await screen.findByText('Trends need at least two full months of history.')).toBeTruthy();
    expect(screen.queryByTestId('bar-chart')).toBeNull();
  });

  it('Forecast shows confidence, the projection table and which goals are at risk', async () => {
    render(<Assistant />);
    await userEvent.click(await screen.findByText('Forecast'));
    expect(await screen.findByText('Medium confidence')).toBeTruthy();
    expect(screen.getByText('R23000')).toBeTruthy();
    expect(screen.getByText('R21000 to R25000')).toBeTruthy();
    expect(screen.getByText('12 months')).toBeTruthy();
    expect(screen.getByText('On track')).toBeTruthy();
    expect(screen.getByText('At risk')).toBeTruthy();
    expect(screen.getByTestId('line-chart')).toBeTruthy();
  });

  it('Forecast shows only the message when there is not enough data', async () => {
    api.get.mockImplementation((url) => Promise.resolve({ data: url === '/api/predictions'
      ? { enoughData: false, message: 'Predictions need at least one full month of income and expense history.' } : advice }));
    render(<Assistant />);
    await userEvent.click(await screen.findByText('Forecast'));
    expect(await screen.findByText(/need at least one full month/)).toBeTruthy();
    expect(screen.queryByTestId('line-chart')).toBeNull();
  });
});
