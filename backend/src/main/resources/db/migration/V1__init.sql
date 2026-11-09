-- SaveWise schema v1 (matches Database Design v1.1; column types completed where the doc left them open)
CREATE TABLE roles (
  role_id BIGSERIAL PRIMARY KEY,
  role_name VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE users (
  user_id BIGSERIAL PRIMARY KEY,
  role_id BIGINT NOT NULL REFERENCES roles(role_id),
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  phone VARCHAR(20),
  monthly_income DECIMAL(12,2) CHECK (monthly_income >= 0),
  profile_picture VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE categories (
  category_id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  type VARCHAR(20) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
  icon VARCHAR(100),
  UNIQUE (name, type)
);

CREATE TABLE income (
  income_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  category_id BIGINT NOT NULL REFERENCES categories(category_id),
  amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
  description TEXT,
  income_date DATE NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE expenses (
  expense_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  category_id BIGINT NOT NULL REFERENCES categories(category_id),
  amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
  payment_method VARCHAR(50),
  description TEXT,
  expense_date DATE NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE budgets (
  budget_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  category_id BIGINT NOT NULL REFERENCES categories(category_id),
  budget_month DATE NOT NULL,
  budget_amount DECIMAL(12,2) NOT NULL CHECK (budget_amount > 0),
  UNIQUE (user_id, category_id, budget_month)
);

CREATE TABLE saving_accounts (
  account_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  account_name VARCHAR(100) NOT NULL,
  current_balance DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (current_balance >= 0),
  target_amount DECIMAL(12,2),
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE saving_transactions (
  transaction_id BIGSERIAL PRIMARY KEY,
  account_id BIGINT NOT NULL REFERENCES saving_accounts(account_id) ON DELETE CASCADE,
  transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('DEPOSIT','WITHDRAW','TRANSFER')),
  amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
  transaction_date DATE NOT NULL,
  description TEXT
);

CREATE TABLE financial_goals (
  goal_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  title VARCHAR(100) NOT NULL,
  target_amount DECIMAL(12,2) NOT NULL CHECK (target_amount > 0),
  current_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  deadline DATE,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','COMPLETED','CANCELLED'))
);

CREATE TABLE recurring_transactions (
  recurring_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  category_id BIGINT NOT NULL REFERENCES categories(category_id),
  amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
  frequency VARCHAR(20) NOT NULL CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY')),
  next_execution DATE NOT NULL,
  transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('INCOME','EXPENSE')),
  description TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE notifications (
  notification_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  title VARCHAR(200) NOT NULL,
  message TEXT NOT NULL,
  type VARCHAR(30) NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE user_preferences (
  preference_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
  theme VARCHAR(20) NOT NULL DEFAULT 'LIGHT',
  currency VARCHAR(10) NOT NULL DEFAULT 'ZAR',
  notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  token_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  token VARCHAR(255) NOT NULL UNIQUE,
  expiry_date TIMESTAMP NOT NULL
);

CREATE INDEX idx_income_user ON income(user_id);
CREATE INDEX idx_income_date ON income(income_date);
CREATE INDEX idx_expenses_user ON expenses(user_id);
CREATE INDEX idx_expenses_date ON expenses(expense_date);
CREATE INDEX idx_budgets_user ON budgets(user_id);
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_goals_user ON financial_goals(user_id);
CREATE INDEX idx_recurring_user ON recurring_transactions(user_id);
CREATE INDEX idx_recurring_next ON recurring_transactions(next_execution);

INSERT INTO roles (role_name) VALUES ('USER'), ('ADMIN');
INSERT INTO categories (name, type) VALUES
 ('Salary','INCOME'),('Freelance','INCOME'),('Investment','INCOME'),('Gift','INCOME'),
 ('Food','EXPENSE'),('Transport','EXPENSE'),('Rent','EXPENSE'),('Utilities','EXPENSE'),
 ('Shopping','EXPENSE'),('Entertainment','EXPENSE'),('Health','EXPENSE'),('Education','EXPENSE');
