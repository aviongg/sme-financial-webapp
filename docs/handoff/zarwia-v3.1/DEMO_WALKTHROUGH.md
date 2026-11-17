# Zarwia v3.1: presentation demo walkthrough

For Suleman to record the demo. Application: `eb4d7f5bea2392cf07bf5d17f3efa551c333e7b8`.

**This walkthrough has not been run on Suleman's machine or on any deployed stack.** It
describes features the v3.1 browser suites exercised (see "Basis" on each step). Figures
depend on the seeded or entered data, so **read the numbers off the screen. Don't script
specific amounts in the narration.**

## Before recording

1. Bring the app up with Path A (`DEPLOYMENT_HANDOFF.md` §5A) and complete its GO checklist.
   Path B works the same way in the browser, except where a step says otherwise.
2. Use Chrome or Edge at 1280×800 or larger, and close unrelated tabs.
3. Rehearse the whole sequence once, then recreate the demo for clean data (`DEPLOYMENT_HANDOFF.md` §10) if you want.
4. **Create and change records only in "(Sandbox)" businesses.** Present the read-only figures from the showcase businesses.

## What's live and what's simulated

Say this on camera, or show it as a caption:

| Area | In this demo | Live integration? |
|---|---|---|
| Accounting, invoices, bills, inventory, projects, reports, health score | Real backend and real PostgreSQL | **Real application logic** with **synthetic data** |
| AI Copilot | Deterministic "Offline assistant": answers built from your records with no AI provider | **No.** OpenAI is not connected. |
| OCR | Automatic extraction is **off**. Uploads go to **manual review**. | **No.** Google Vision is not connected. |
| WhatsApp | Path A: **simulated** (mock provider). Path B: **disabled**. | **No.** Nothing is sent. |
| Email (password reset) | Path A: off | No |
| Mobile camera capture | A camera entry exists, but real phone behaviour is untested (DEF-15) | Not demonstrated |

## Steps

Pages are given by URL path; the sidebar links to all of them.

### 1. Sign in and choose a business
- **Prerequisite:** the demo is running. `start-demo.ps1` printed the accounts.
- **Do:** open `http://localhost:3000` and sign in as `demo.owner@finsight.local`.
- **Expected:**
  - The dashboard (`/`) loads, and the active business name is visible in the header.
  - The business selector lists the seeded showcase and "(Sandbox)" businesses.
  - The Zarwia wordmark is visible.
- **Basis:** product suite (DEF-10, DEF-20); DEF-11 multi-business sessions.
- *Optional:* sign in as `demo.viewer@finsight.local` in a private window to show the read-only role (DEF-17).

### 2. Financial dashboard and health score
- **Prerequisite:** a showcase business is active.
- **Do:** show `/`, then open `/health/components`.
- **Expected:**
  - Monthly figures come **from daily activity**; nobody typed monthly totals.
  - The health score and its component breakdown appear.
  - The action-center items show their reason and evidence.
- **Basis:** the product suite checks that the derived month equals the P&L, that a late entry updates the earlier month and its score, and that manual monthly totals are refused (409).

### 3. Financial accounts and transactions
- **Do:** open `/finance/accounts`, then `/finance/transactions`.
- **Expected:** account balances and the transaction list for the active business, with translated activity labels (DEF-09).
- **Basis:** DEF-09 EN/UR checks with exact backend amounts.

### 4. Invoice, bill and payment
- **Prerequisite:** switch to a **(Sandbox)** business. Use the Owner or Accountant role.
- **Do:**
  1. In `/invoices/new`, create an invoice with a line item and save or finalize it.
  2. Open the related receivable from the invoice (DEF-14).
  3. In `/bills/new`, create a bill and open its payable.
  4. Record a payment or receipt where the detail page offers it.
- **Expected:**
  - The invoice links to its receivable and the bill links to its payable.
  - The outstanding amount changes by exactly the amount recorded.
  - Payables and receivables (`/records/payables`, `/records/receivables`) reflect the new records.
- **Basis:** product suite (credit note reduces the outstanding amount, invoice recognized) and DEF-14 navigation.
- **Caution:** the payment action's placement wasn't individually listed in the v3.1 evidence. Rehearse this step.

### 5. Inventory and project costing
- **Do:**
  1. Open `/inventory` and `/inventory/stock`.
  2. In a Sandbox business, record a stock receipt.
  3. Optionally, pick an inventory item on an invoice line.
  4. Open `/projects`, choose a project, and show its costs or actuals.
- **Expected:**
  - Stock quantity and value update at weighted-average cost.
  - Invoice fulfilment issues stock and records cost of goods sold.
  - The project shows actual cost against recovery.
- **Basis:** product suite F08 (WAC, COGS, keyboard receipt), DEF-05/06; project browser suites.

### 6. Cash flow and financial reports
- **Do:** open `/finance/forecast`, then `/reports`. Generate the financial pack and download the P&L CSV.
- **Expected:** all report sections render, and the CSV downloads. Figures match the dashboard for the same period.
- **Basis:** product suite F12B financial pack; historical financial-risk browser checks.

### 7. Documents: OCR and evidence
- **Prerequisite:** a small synthetic PDF, JPEG or PNG receipt. Never use a real customer document.
- **Do:**
  1. Upload it in `/upload`.
  2. Open it in `/upload/<id>`.
  3. Enter or correct the fields in manual review and confirm.
  4. On an invoice or bill, attach it with the evidence selector (listed by filename and date).
- **Expected:**
  - Without OCR, the document goes to **manual review**.
  - Confirming makes it evidence **without** creating ledger transactions. The app states that linking evidence is not posting.
  - The linked evidence opens the uploaded document.
- **Say on camera:** "Automatic OCR is disabled in this environment; this is the manual-review path."
- **Basis:** DEF-01, DEF-12, DEF-13 and DEF-14 product checks.

### 8. DEF-11: draft protection when switching business
- **Prerequisite:** the owner account with at least two businesses. Use **(Sandbox)** businesses.
- **Do:**
  1. In tab 1, start a new invoice in Sandbox A and type some details. Don't save.
  2. Open tab 2 and switch the active business to Sandbox B.
  3. Return to tab 1.
- **Expected:**
  - Tab 1 shows a business-changed dialog instead of silently losing or mis-saving the draft.
  - Choosing to return to the original business restores the draft, and saving puts it in **Sandbox A only**.
  - A clean form, with nothing typed, switches without a dialog.
- **Basis:** DEF-11 suite, 9/9 real two-tab journeys, including Urdu and a mobile viewport.

### 9. AI Copilot (deterministic fallback)
- **Prerequisite:** the business's AI module is enabled. It is enabled for seeded businesses.
- **Do:** open `/ask-finsight`. Click a suggestion such as "What is my profit this month?" or type a question.
- **Expected:**
  - The mode badge reads **"Offline assistant"**.
  - Each answer is labelled "Offline assistant answer — built from your records without an AI provider" and lists its sources.
  - The figures match the reports.
  - No external AI call is made.
- **Basis:** product suite (grounded figure with cited evidence) and the provider-disclosure suite (labels, Urdu fallback).

### 10. WhatsApp provider disclosure
- **Do:** open `/settings` and find the WhatsApp delivery section.
- **Expected:**
  - **Path A:** it says *"Simulated: a test provider is active. No WhatsApp messages are transmitted, even if a delivery shows as sent."*
  - **Path B:** it says *"WhatsApp delivery is disabled: no provider is configured, so no messages are sent."*
- **Basis:** provider-disclosure suite 10/10, with mock, disabled, Meta and unknown states in EN/UR.

### 11. English and Urdu
- **Do:** use the language control in the page header to switch to اردو. Revisit the dashboard, an invoice and the Copilot.
- **Expected:**
  - The layout becomes right-to-left and labels are translated.
  - **Amounts are identical** to the English view.
  - Switch back to English at the end.
- **Basis:** 72 EN/UR responsive page loads with no overflow or console errors, and DEF-09/16/20 checks.

## If something goes wrong while recording

- If a page errors, check that `start-demo.ps1` is still running, then troubleshoot with `DEPLOYMENT_HANDOFF.md` §11.
- For clean data, recreate the demo (`DEPLOYMENT_HANDOFF.md` §10). Don't try to edit the database by hand.
- Don't present any feature on this page as a live third-party integration.
