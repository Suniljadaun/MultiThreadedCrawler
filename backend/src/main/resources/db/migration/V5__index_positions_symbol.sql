-- Holder lookup on every price change (PositionRepository.findHolderIds).
-- quantity is included so the query is answered from the index alone.
CREATE INDEX ix_positions_symbol_user ON positions (symbol, user_id) INCLUDE (quantity);
