-- start_date is the anchor that keeps monthly/yearly schedules on the right day
-- (a bill that starts on the 31st returns to the 31st after short months).
ALTER TABLE recurring_transactions ADD COLUMN start_date DATE;
UPDATE recurring_transactions SET start_date = next_execution WHERE start_date IS NULL;
ALTER TABLE recurring_transactions ALTER COLUMN start_date SET NOT NULL;
ALTER TABLE recurring_transactions ADD COLUMN end_date DATE;
ALTER TABLE recurring_transactions
  ADD CONSTRAINT chk_recurring_dates CHECK (end_date IS NULL OR end_date >= start_date);

-- Which schedule created a record (for history). Deleting a schedule keeps the records it created.
ALTER TABLE income   ADD COLUMN recurring_id BIGINT REFERENCES recurring_transactions(recurring_id) ON DELETE SET NULL;
ALTER TABLE expenses ADD COLUMN recurring_id BIGINT REFERENCES recurring_transactions(recurring_id) ON DELETE SET NULL;

-- A schedule can never post twice for the same date, even if two app instances run the job at once.
CREATE UNIQUE INDEX uq_income_recurring_date   ON income(recurring_id, income_date)     WHERE recurring_id IS NOT NULL;
CREATE UNIQUE INDEX uq_expenses_recurring_date ON expenses(recurring_id, expense_date)  WHERE recurring_id IS NOT NULL;
