-- Add deleted_at column to portfolios table for soft delete functionality

ALTER TABLE portfolios ADD COLUMN deleted_at TIMESTAMP NULL;

-- Add index for deleted_at to optimize queries filtering out deleted portfolios
CREATE INDEX idx_portfolios_deleted_at ON portfolios(deleted_at);

-- Add comment to the column
COMMENT ON COLUMN portfolios.deleted_at IS 'Soft delete timestamp. NULL means not deleted. Non-NULL means deleted and will be hard-deleted after 30 days.';