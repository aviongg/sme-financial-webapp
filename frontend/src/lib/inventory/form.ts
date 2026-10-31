// F08 Phase A: inventory catalog drafts and the valuation-preview sandbox.
// Catalog items carry identity only (no stock, value or cost). All valuation numbers come from the server's
// stateless preview; the browser never computes or stores an authoritative value.

export type InventoryItemKind = 'RAW_MATERIAL' | 'RESALE_GOOD' | 'CONSUMABLE' | 'FINISHED_GOOD';
export type InventoryItemStatus = 'ACTIVE' | 'ARCHIVED';
export type ValuationStepType = 'RECEIPT' | 'ISSUE';

export const inventoryItemKinds: InventoryItemKind[] = ['RAW_MATERIAL', 'RESALE_GOOD', 'CONSUMABLE', 'FINISHED_GOOD'];
export const inventoryItemStatuses: InventoryItemStatus[] = ['ACTIVE', 'ARCHIVED'];

export const MAX_SKU_LENGTH = 40;
export const MAX_NAME_LENGTH = 160;
export const MAX_UNIT_LENGTH = 40;
export const MAX_CATEGORY_LENGTH = 80;
export const MAX_PREVIEW_STEPS = 100;

// Mirrors the server contract: quantity up to 9 integer + 6 fractional digits; value up to 12 + 4.
const SKU_PATTERN = /^[A-Z0-9][A-Z0-9._/-]{0,39}$/;
const QUANTITY_PATTERN = /^[0-9]{1,9}(?:\.[0-9]{1,6})?$/;
const VALUE_PATTERN = /^[0-9]{1,12}(?:\.[0-9]{1,4})?$/;

export type InventoryItemErrorCode = 'required' | 'sku' | 'text' | 'quantity' | 'value' | 'positive' | 'insufficient' | 'invalid';
export type InventoryValidationErrors = Partial<Record<string, InventoryItemErrorCode>>;

export interface InventoryItemDraft {
  sku: string;
  name: string;
  unit: string;
  itemKind: InventoryItemKind;
  category: string;
  reorderLevel: string;
}

export interface InventoryItemPayload {
  sku: string;
  name: string;
  unit: string;
  itemKind: InventoryItemKind;
  category: string | null;
  reorderLevel: string | null;
}

export interface InventoryItemSource {
  sku: string;
  name: string;
  unit: string;
  itemKind: InventoryItemKind;
  category: string | null;
  reorderLevel: string | null;
}

export function initialItemDraft(): InventoryItemDraft {
  return { sku: '', name: '', unit: '', itemKind: 'RAW_MATERIAL', category: '', reorderLevel: '' };
}

export function draftFromItem(item: InventoryItemSource): InventoryItemDraft {
  return {
    sku: item.sku,
    name: item.name,
    unit: item.unit,
    itemKind: item.itemKind,
    category: item.category ?? '',
    reorderLevel: item.reorderLevel ?? '',
  };
}

export function normalizeSku(value: string): string {
  return value.trim().toUpperCase();
}

function hasControl(value: string): boolean {
  return [...value].some(character => {
    const code = character.codePointAt(0) ?? 0;
    return code < 0x20 || (code >= 0x7f && code <= 0x9f);
  });
}

function text(value: string, max: number, required: boolean): InventoryItemErrorCode | null {
  const trimmed = value.trim();
  if (!trimmed) return required ? 'required' : null;
  return trimmed.length > max || hasControl(trimmed) ? 'text' : null;
}

export function validateItemDraft(draft: InventoryItemDraft): InventoryValidationErrors {
  const errors: InventoryValidationErrors = {};
  const sku = normalizeSku(draft.sku);
  if (!sku) errors.sku = 'required';
  else if (!SKU_PATTERN.test(sku)) errors.sku = 'sku';
  const name = text(draft.name, MAX_NAME_LENGTH, true);
  if (name) errors.name = name;
  const unit = text(draft.unit, MAX_UNIT_LENGTH, true);
  if (unit) errors.unit = unit;
  const category = text(draft.category, MAX_CATEGORY_LENGTH, false);
  if (category) errors.category = category;
  const reorder = draft.reorderLevel.trim();
  if (reorder && !QUANTITY_PATTERN.test(reorder)) errors.reorderLevel = 'quantity';
  if (!inventoryItemKinds.includes(draft.itemKind)) errors.itemKind = 'invalid';
  return errors;
}

export function itemPayload(draft: InventoryItemDraft): InventoryItemPayload {
  return {
    sku: normalizeSku(draft.sku),
    name: draft.name.trim(),
    unit: draft.unit.trim(),
    itemKind: draft.itemKind,
    category: draft.category.trim() || null,
    reorderLevel: draft.reorderLevel.trim() || null,
  };
}

export function itemUpdatePayload(draft: InventoryItemDraft, expectedVersion: number) {
  return { ...itemPayload(draft), expectedVersion };
}

/** Maps server field paths onto the closed set of form error codes. */
export function mapInventoryFieldErrors(fieldErrors: Record<string, string> | undefined): InventoryValidationErrors {
  const mapped: InventoryValidationErrors = {};
  for (const [field, code] of Object.entries(fieldErrors ?? {})) {
    mapped[field] = code === 'required' ? 'required'
      : code === 'sku_invalid' ? 'sku'
        : code === 'text_invalid' ? 'text'
          : code === 'quantity_invalid' ? 'positive'
            : code === 'insufficient_stock' ? 'insufficient'
            : code === 'decimal_invalid' ? (field.endsWith('value') || field.endsWith('Value') ? 'value' : 'quantity')
              : 'invalid';
  }
  return mapped;
}

export function isInventoryConflictCode(code: string | undefined): boolean {
  return code === 'stale_inventory_item_version' || code === 'duplicate_inventory_sku' || code === 'invalid_inventory_item_state';
}

export interface PreviewStepDraft {
  key: string;
  type: ValuationStepType;
  quantity: string;
  value: string;
}

export interface PreviewDraft {
  openingQuantity: string;
  openingValue: string;
  steps: PreviewStepDraft[];
}

let stepSequence = 0;
export function newPreviewStep(type: ValuationStepType): PreviewStepDraft {
  stepSequence += 1;
  return { key: `step-${stepSequence}`, type, quantity: '', value: '' };
}

export function initialPreviewDraft(): PreviewDraft {
  return { openingQuantity: '0', openingValue: '0', steps: [newPreviewStep('RECEIPT')] };
}

/** Client-side shape checks only; insufficient stock and all arithmetic are decided by the server. */
export function validatePreviewDraft(draft: PreviewDraft): InventoryValidationErrors {
  const errors: InventoryValidationErrors = {};
  const quantity = draft.openingQuantity.trim();
  const value = draft.openingValue.trim();
  if (!quantity) errors.openingQuantity = 'required';
  else if (!QUANTITY_PATTERN.test(quantity)) errors.openingQuantity = 'quantity';
  if (!value) errors.openingValue = 'required';
  else if (!VALUE_PATTERN.test(value)) errors.openingValue = 'value';
  if (draft.steps.length > MAX_PREVIEW_STEPS) errors.steps = 'invalid';
  draft.steps.forEach((step, index) => {
    const q = step.quantity.trim();
    if (!q) errors[`steps[${index}].quantity`] = 'required';
    else if (!QUANTITY_PATTERN.test(q)) errors[`steps[${index}].quantity`] = 'quantity';
    else if (Number(q) === 0) errors[`steps[${index}].quantity`] = 'positive';
    if (step.type === 'RECEIPT') {
      const v = step.value.trim();
      if (!v) errors[`steps[${index}].value`] = 'required';
      else if (!VALUE_PATTERN.test(v)) errors[`steps[${index}].value`] = 'value';
    }
  });
  return errors;
}

export function previewPayload(draft: PreviewDraft) {
  return {
    openingQuantity: draft.openingQuantity.trim(),
    openingValue: draft.openingValue.trim(),
    // An issue never carries a value: the calculator derives it.
    steps: draft.steps.map(step => step.type === 'RECEIPT'
      ? { type: step.type, quantity: step.quantity.trim(), value: step.value.trim() }
      : { type: step.type, quantity: step.quantity.trim() }),
  };
}
