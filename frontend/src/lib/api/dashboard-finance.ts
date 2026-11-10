import { apiClient } from './client';
import { isAccountId, isCalendarDate } from './dev-a/account-contracts';
import { isSupportedCurrency, currencyScales } from '../utils/decimal-display';
export type EvidenceState<T> = { status:'AVAILABLE';data:T;asOf:string;warnings:string[];completeness:'COMPLETE'|'PARTIAL'|'EMPTY' } | {status:'UNAVAILABLE';data:null;asOf:string;warnings:string[];completeness:'COMPLETE'|'PARTIAL'|'EMPTY'};
export interface CashByCurrency {currency:string;currencyDecimals:number;currentBalance:string;accountCount:number}
export function exactDisplayAmount(value:unknown,scale=2):string|null{
 if(typeof value!=='string'&&typeof value!=='number')return null;
 const text=String(value),match=/^(-?)(0|[1-9][0-9]*)(?:\.([0-9]+))?$/.exec(text);
 if(!match||text.length>128||(match[3]??'').length>scale)return null;
 const result=match[1]+match[2]+(scale?'.'+(match[3]??'').padEnd(scale,'0'):'');
 return /^-0(?:\.0+)?$/.test(result)?null:result;
}
export function formatDashboardAmount(value:string):string{
 const [whole,fraction]=value.split('.');return whole.replace(/\B(?=(\d{3})+(?!\d))/g,',')+(fraction===undefined?'':'.'+fraction);
}
export function parseCashEvidence(raw:unknown,businessId:string):EvidenceState<CashByCurrency[]>{
 if(!raw||typeof raw!=='object')throw new Error('Invalid financial evidence.');
 const e=raw as Record<string,unknown>;
 if(e.businessId!==businessId||!isAccountId(e.businessId)||!isCalendarDate(e.asOfDate)||!Array.isArray(e.warnings)||!e.warnings.every(w=>typeof w==='string'))throw new Error('Invalid financial context.');
 if(!['COMPLETE','PARTIAL','EMPTY'].includes(e.completeness as string))throw new Error('Invalid completeness metadata.');
 const completeness=e.completeness as 'COMPLETE'|'PARTIAL'|'EMPTY';
 if(e.status==='UNAVAILABLE')return {status:'UNAVAILABLE',data:null,asOf:e.asOfDate,warnings:e.warnings as string[],completeness};
 if(e.status!=='AVAILABLE'||!e.data||typeof e.data!=='object')throw new Error('Invalid financial availability.');
 const data=e.data as Record<string,unknown>;if(!Array.isArray(data.currencySummaries))throw new Error('Invalid cash summaries.');
 const currencies=new Set<string>();
 const rows=data.currencySummaries.map((item:unknown)=>{
  const c=item as CashByCurrency;if(!c||typeof c.currency!=='string'||!isSupportedCurrency(c.currency)||c.currencyDecimals!==currencyScales[c.currency]||typeof c.currentBalance!=='string'||exactDisplayAmount(c.currentBalance,c.currencyDecimals)!==c.currentBalance||!Number.isSafeInteger(c.accountCount)||c.accountCount<0||currencies.has(c.currency))throw new Error('Invalid currency summary.');
  currencies.add(c.currency);return {currency:c.currency,currencyDecimals:c.currencyDecimals,currentBalance:c.currentBalance,accountCount:c.accountCount};
 });
 return {status:'AVAILABLE',data:rows,asOf:e.asOfDate,warnings:e.warnings as string[],completeness};
}
export const dashboardFinanceApi={
 cash:async(businessId:string,signal?:AbortSignal)=>parseCashEvidence(await apiClient<unknown>('/finance/query/cash-position',{signal}),businessId),
};
/** C02/C04 are independently versioned; absence never substitutes planned values for actuals. */
export interface FinancialCapabilityBoundary { available:boolean; reason:'CONTRACT_NOT_PRESENT'|'UNAVAILABLE'|null }
export const dashboardC02Boundary:FinancialCapabilityBoundary={available:false,reason:'CONTRACT_NOT_PRESENT'};

export function projectProfitability(value:{profitabilityStatus:string;actualGrossProfit:unknown}):string|null{
 return value.profitabilityStatus==='AVAILABLE'&&value.actualGrossProfit!==null?exactDisplayAmount(value.actualGrossProfit):null;
}
