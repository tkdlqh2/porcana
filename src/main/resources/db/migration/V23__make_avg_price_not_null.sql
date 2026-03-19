-- avg_price를 NOT NULL로 변경
-- seedMoney 복원 계산에 필수이므로 non-null이 맞음

-- 기존 null 데이터가 있다면 현재가로 backfill (실제로는 setSeed를 통해 생성되므로 null이 없어야 함)
UPDATE portfolio_holding_baseline_items pbi
SET avg_price = (
    SELECT ap.close_price
    FROM asset_prices ap
    WHERE ap.asset_id = pbi.asset_id
    ORDER BY ap.price_date DESC
    LIMIT 1
)
WHERE avg_price IS NULL;

-- NOT NULL 제약 추가
ALTER TABLE portfolio_holding_baseline_items
    ALTER COLUMN avg_price SET NOT NULL;

COMMENT ON COLUMN portfolio_holding_baseline_items.avg_price IS '평균 매수가 (필수)';