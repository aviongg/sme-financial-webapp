# V52 production grant acceptance inventory

84 tables are checked against explicit V1–V52 GRANT/REVOKE statements plus the existing bootstrap default runtime DML and backup SELECT. This documents current migration behavior, not a new accounting policy. Migrator owns the schema/domain objects. Backup has no mutation/DDL privileges. Trigger guards are verified separately; an ACL permitting UPDATE does not override an append-only trigger.

| Table | Source migration | Runtime ACL | Backup ACL | Trigger count |
|---|---|---|---|---|
| account_movements | V20__create_financial_transactions.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 1 |
| app_users | V9__add_identity_and_authentication.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 1 |
| bank_reconciliation_matches | V28__create_bank_reconciliation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| bank_statement_entries | V28__create_bank_reconciliation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| bank_statement_imports | V28__create_bank_reconciliation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| business_budget_lines | V48__classifications_budgets_goals.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 1 |
| business_budget_revisions | V48__classifications_budgets_goals.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 1 |
| business_budgets | V48__classifications_budgets_goals.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| business_configuration | V30__business_configuration.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| business_memberships | V9__add_identity_and_authentication.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| business_profiles | V1__initial_schema.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| businesses | V9__add_identity_and_authentication.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| category_cost_behaviors | V48__classifications_budgets_goals.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| commercial_adjustment_sequences | V45__commercial_lifecycle_completion.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| commercial_adjustments | V45__commercial_lifecycle_completion.sql | SELECT, INSERT, UPDATE | SELECT | 3 |
| commercial_document_evidence_links | V51__commercial_document_ocr_evidence.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| commercial_document_lines | V34__commercial_documents_foundation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 2 |
| commercial_documents | V34__commercial_documents_foundation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 2 |
| commercial_number_sequences | V34__commercial_documents_foundation.sql | SELECT, INSERT, UPDATE | SELECT | 2 |
| commission_advances | V38__create_salesperson_and_commission_foundation.sql | SELECT, INSERT, UPDATE | SELECT | 1 |
| commission_earnings | V38__create_salesperson_and_commission_foundation.sql | SELECT, INSERT, UPDATE | SELECT | 1 |
| commission_payouts | V38__create_salesperson_and_commission_foundation.sql | SELECT, INSERT, UPDATE | SELECT | 1 |
| commission_rules | V38__create_salesperson_and_commission_foundation.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| counterparties | V25__create_counterparties.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| counterparty_advances | V27__create_payment_cycles_and_advances.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| dashboard_preferences | V31__dashboard_preferences.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| document_corrections | V17__business_team_and_document_provenance.sql | SELECT, INSERT | SELECT | 2 |
| document_financial_contributions | V32__monthly_record_integrity.sql | SELECT, INSERT | SELECT | 2 |
| estimate_commercial_links | V49__estimate_commercial_lineage.sql | SELECT, INSERT, DELETE | SELECT | 0 |
| estimate_lines | V36__persisted_estimates_and_conversion.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 2 |
| estimate_project_conversions | V36__persisted_estimates_and_conversion.sql | SELECT, INSERT | SELECT | 2 |
| estimate_versions | V36__persisted_estimates_and_conversion.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 2 |
| estimates | V36__persisted_estimates_and_conversion.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 2 |
| financial_accounts | V18__create_financial_accounts.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 1 |
| financial_classifications | V48__classifications_budgets_goals.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| financial_goals | V48__classifications_budgets_goals.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| financial_idempotency_records | V19__create_financial_idempotency.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| financial_obligations | V26__create_financial_obligations.sql | SELECT, INSERT, UPDATE | SELECT | 1 |
| financial_transactions | V20__create_financial_transactions.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 1 |
| flyway_schema_history | Flyway; V15 explicitly revokes runtime access; backup SELECT retained | none | SELECT | 0 |
| fx_internal_transfers | V41__add_multicurrency_internal_transfers.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| fx_obligation_recognition_evidence | V40__create_fx_recognition_and_reconciliation_schema.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| fx_obligation_settlements | V39__create_fx_settlements_schema.sql | SELECT, INSERT, UPDATE | SELECT | 1 |
| import_batch_rows | V24__create_import_draft_provenance.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| import_batches | V24__create_import_draft_provenance.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| insights | V4__create_insights_table.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| inventory_items | V37__inventory_catalog_foundation.sql | SELECT, INSERT, UPDATE | SELECT | 2 |
| inventory_movements | V46__perpetual_inventory_and_cogs.sql | SELECT, INSERT | SELECT | 3 |
| inventory_positions | V46__perpetual_inventory_and_cogs.sql | SELECT, INSERT, UPDATE | SELECT | 2 |
| monthly_financing_inputs | V52__transaction_driven_monthly_projection.sql | SELECT, INSERT, UPDATE | SELECT | 1 |
| monthly_projection_state | V52__transaction_driven_monthly_projection.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| monthly_records | V1__initial_schema.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| obligation_payment_allocations | V26__create_financial_obligations.sql | SELECT, INSERT, UPDATE | SELECT | 1 |
| obligation_payment_schedules | V27__create_payment_cycles_and_advances.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| password_reset_tokens | V13__add_password_reset_tokens.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| project_budget_lines | V33__project_planning_foundation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 2 |
| project_budget_versions | V33__project_planning_foundation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 2 |
| project_commercial_document_links | V43__project_commitments_and_commercial_linkage.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| project_financial_attributions | V42__project_actual_financial_attribution.sql | SELECT, INSERT, UPDATE | SELECT | 1 |
| project_financial_obligation_links | V43__project_commitments_and_commercial_linkage.sql | SELECT, INSERT, UPDATE | SELECT | 1 |
| project_ocr_evidence_links | V44__project_ocr_evidence_links.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| projects | V33__project_planning_foundation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 2 |
| recommendations | V5__create_recommendations_table.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| record_drafts | V21__create_record_drafts.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| recurring_rule_occurrences | V29__create_recurring_transactions.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| recurring_rules | V29__create_recurring_transactions.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| sales_assignments | V38__create_salesperson_and_commission_foundation.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| salesperson_profiles | V38__create_salesperson_and_commission_foundation.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| sample_activities | V35__sample_cost_planning_foundation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 2 |
| sample_actual_costs | V47__sample_actuals_and_recovery.sql | SELECT, INSERT, UPDATE | SELECT | 2 |
| sample_cost_plan_lines | V35__sample_cost_planning_foundation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 3 |
| sample_cost_plan_versions | V35__sample_cost_planning_foundation.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 3 |
| sample_recovery_links | V47__sample_actuals_and_recovery.sql | SELECT, INSERT, UPDATE | SELECT | 2 |
| score_results | V1__initial_schema.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| security_audit_events | V12__add_security_audit_events.sql | SELECT, INSERT | SELECT | 2 |
| spring_session | V9__add_identity_and_authentication.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| spring_session_attributes | V9__add_identity_and_authentication.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| uploaded_documents | V1__initial_schema.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 1 |
| user_mfa | V14__add_platform_role_and_mfa.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| user_mfa_recovery_codes | V14__add_platform_role_and_mfa.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| whatsapp_conversations | V50__whatsapp_inbound_conversations.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| whatsapp_deliveries | V8__add_whatsapp_delivery.sql | SELECT, INSERT, UPDATE, DELETE | SELECT | 0 |
| whatsapp_inbound_links | V50__whatsapp_inbound_conversations.sql | SELECT, INSERT, UPDATE | SELECT | 0 |
| whatsapp_inbound_messages | V50__whatsapp_inbound_conversations.sql | SELECT, INSERT, UPDATE | SELECT | 0 |

**Owner decision retained:** V49 `estimate_commercial_links` inherits direct DELETE from the default runtime DML grants: its explicit REVOKE removes UPDATE/TRUNCATE, not DELETE. V49 documents draft-deletion cascading, but does not explicitly settle direct lineage deletion. No SQL is changed; Claude/product owner should confirm the intended direct-DELETE contract before any V53+ proposal. The acceptance inventory records the actual declared grants rather than silently inventing a stronger policy.

Fresh disposable catalog agrees with the explicit migration/default ACL model: True. Production PostgreSQL 17/TLS and actual operator provisioning remain separate acceptance gates.
