-- V8: Parcelamento e tipo/número de documento nas transações financeiras

ALTER TABLE financial_transactions ADD COLUMN document_type VARCHAR(20);
ALTER TABLE financial_transactions ADD COLUMN document_number VARCHAR(50);
ALTER TABLE financial_transactions ADD COLUMN installment_current INTEGER;
ALTER TABLE financial_transactions ADD COLUMN installment_total INTEGER;
