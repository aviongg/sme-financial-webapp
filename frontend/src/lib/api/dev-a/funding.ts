import { apiClient, ApiError } from '../client';
import { normalizeAmountInput } from '../../utils/amount-input';
import { isCalendarDate } from './account-contracts';
import { endDate, eventErrors } from '../../../components/finance/funding/funding-input';
import type { FundingEditorInput } from '../../../components/finance/funding/FundingWorkspace';
import type { FundingComparisonView, FundingScenarioView } from '../../../components/finance/funding/FundingResults';
export class InvalidFundingResponse extends Error { constructor() { super('Invalid funding scenario response.'); this.name = 'InvalidFundingResponse'; } }
export interface WirePlannedCashEventInput { eventId:string; date:string; direction:'INFLOW'|'OUTFLOW'; amount:string; description:string }
export interface WireScenarioDefinition { scenarioName:string; events:WirePlannedCashEventInput[] }
export interface WireFundingScenarioCompareRequest { currency:'PKR'; asOfDate:string; horizonDays:number; openingLiquidity:{source:'LEDGER_CASH'|'MANUAL_ASSUMPTION';manualAmount?:string}; baselineScenario:WireScenarioDefinition; alternativeScenario:WireScenarioDefinition }
export interface WireDailyFundingPosition { date:string;openingPosition:string;plannedInflows:string;plannedOutflows:string;netPlannedMovement:string;closingPosition:string;cumulativeProjectedPosition:string;deficit:string;isShortfall:boolean;eventsOnDay:unknown[] }
export interface WireScenarioResult { scenarioName:string;totalPlannedInflows:string;totalPlannedOutflows:string;netPlannedMovement:string;closingProjectedLiquidity:string;peakFundingRequirement:string;peakDeficitDate:string|null;firstDeficitDate:string|null;recoveryDate:string|null;deficitDayCount:number;hasDeficit:boolean;isRecovered:boolean;timeline:WireDailyFundingPosition[] }
function ensure(v:unknown):asserts v { if(!v)throw new InvalidFundingResponse(); }
const object=(v:unknown):Record<string,unknown>=>{ensure(v&&typeof v==='object'&&!Array.isArray(v));return v as Record<string,unknown>;};
export function fundingAmount(v:unknown,nonnegative=false):v is string {return typeof v==='string'&&v.length<=128&&/^-?(0|[1-9][0-9]*)\.[0-9]{2}$/.test(v)&&v!=='-0.00'&&(!nonnegative||!v.startsWith('-'));}
export function fundingPayload(input:FundingEditorInput):WireFundingScenarioCompareRequest {
 const a=input.assumptions,finish=endDate(a.startDate,a.horizonDays);
 if(!isCalendarDate(a.startDate)||!Number.isInteger(a.horizonDays)||a.horizonDays<7||a.horizonDays>90||!finish||!['LEDGER','MANUAL'].includes(a.source))throw new Error('Invalid funding assumptions.');
 const events=(list:FundingEditorInput['baseline']):WirePlannedCashEventInput[]=>{
  if(list.length>100||new Set(list.map(e=>e.id)).size!==list.length)throw new Error('Invalid event identities.');
  return list.map(e=>{if(!e.id.trim()||!['IN','OUT'].includes(e.direction)||Object.keys(eventErrors(e,a.startDate,finish)).length)throw new Error('Invalid event.');
   return {eventId:e.id,date:e.date,direction:e.direction==='IN'?'INFLOW':'OUTFLOW',amount:normalizeAmountInput(e.amountText,'PKR')!.canonical,description:e.description.trim()};});
 };
 const opening=normalizeAmountInput(a.openingText,'PKR');
 if(a.source==='MANUAL'&&(!opening||opening.canonical.startsWith('-')))throw new Error('Invalid opening assumption.');
 return {currency:'PKR',asOfDate:a.startDate,horizonDays:a.horizonDays,openingLiquidity:a.source==='LEDGER'?{source:'LEDGER_CASH'}:{source:'MANUAL_ASSUMPTION',manualAmount:opening!.canonical},baselineScenario:{scenarioName:'Baseline',events:events(input.baseline)},alternativeScenario:{scenarioName:'Alternative',events:events(input.alternative)}};
}
/** Actual Java DTO names; validate evidence and dates without recalculating financial totals. */
export function parseFundingResponse(value:unknown,request:WireFundingScenarioCompareRequest):FundingComparisonView {
 const r=object(value),o=object(r.openingLiquidity),p=object(r.provenance),source=request.openingLiquidity.source==='LEDGER_CASH'?'POSTED_ACTUAL':'MANUAL_ASSUMPTION';
 ensure(r.currency==='PKR'&&r.asOfDate===request.asOfDate&&r.horizonStart===request.asOfDate&&r.horizonEnd===endDate(request.asOfDate,request.horizonDays)&&r.horizonDays===request.horizonDays);
 ensure(o.source===source&&fundingAmount(o.amount)&&typeof o.note==='string');
 ensure(source!=='MANUAL_ASSUMPTION'||o.amount===request.openingLiquidity.manualAmount);
 ensure(p.openingLiquiditySource===source&&p.planningMode==='HYPOTHETICAL_SIMULATION'&&p.calculationEngine==='funding-scenario-v1'&&typeof p.calculatedAt==='string'&&Number.isFinite(Date.parse(p.calculatedAt)));
 for(const key of ['warnings','limitations'])ensure(Array.isArray(r[key])&&(r[key] as unknown[]).every(x=>typeof x==='string'));
 const scenario=(v:unknown,name:string):{wire:WireScenarioResult;view:FundingScenarioView}=>{
  const s=object(v);ensure(s.scenarioName===name);
  for(const k of ['totalPlannedInflows','totalPlannedOutflows','peakFundingRequirement'])ensure(fundingAmount(s[k],true));
  for(const k of ['netPlannedMovement','closingProjectedLiquidity'])ensure(fundingAmount(s[k]));
  for(const k of ['peakDeficitDate','firstDeficitDate','recoveryDate'])ensure(s[k]===null||isCalendarDate(s[k])&&(s[k] as string)>=request.asOfDate&&(s[k] as string)<=(r.horizonEnd as string));
  ensure(Number.isInteger(s.deficitDayCount)&&(s.deficitDayCount as number)>=0&&(s.deficitDayCount as number)<=request.horizonDays+1&&typeof s.hasDeficit==='boolean'&&s.hasDeficit===((s.deficitDayCount as number)>0));
  ensure(typeof s.isRecovered==='boolean'&&s.isRecovered===(s.recoveryDate!==null)&&s.hasDeficit===(s.firstDeficitDate!==null)&&s.hasDeficit===(s.peakDeficitDate!==null));
  ensure(!s.recoveryDate||(s.recoveryDate as string)>=(s.firstDeficitDate as string));
  ensure(Array.isArray(s.timeline)&&s.timeline.length===request.horizonDays+1);
  for(const [i,item]of(s.timeline as unknown[]).entries()){
   const d=object(item);ensure(d.date===endDate(request.asOfDate,i));
   for(const k of ['openingPosition','netPlannedMovement','closingPosition','cumulativeProjectedPosition'])ensure(fundingAmount(d[k]));
   for(const k of ['plannedInflows','plannedOutflows','deficit'])ensure(fundingAmount(d[k],true));
   ensure(d.cumulativeProjectedPosition===d.closingPosition&&typeof d.isShortfall==='boolean'&&d.isShortfall===(d.closingPosition as string).startsWith('-'));
   ensure(Array.isArray(d.eventsOnDay));for(const raw of d.eventsOnDay as unknown[]){const e=object(raw);ensure(typeof e.eventId==='string'&&e.date===d.date&&['INFLOW','OUTFLOW'].includes(e.direction as string)&&fundingAmount(e.amount,true)&&e.amount!=='0.00'&&(e.description===null||typeof e.description==='string')&&(e.category===null||typeof e.category==='string'));}
  }
  ensure((s.timeline as WireDailyFundingPosition[]).at(-1)!.closingPosition===s.closingProjectedLiquidity);
  return {wire:s as unknown as WireScenarioResult,view:{peak:s.peakFundingRequirement as string,peakDate:s.peakDeficitDate as string|null,firstDeficit:s.firstDeficitDate as string|null,recovery:s.recoveryDate as string|null,closing:s.closingProjectedLiquidity as string,inflows:s.totalPlannedInflows as string,outflows:s.totalPlannedOutflows as string}};
 };
 const b=scenario(r.baseline,request.baselineScenario.scenarioName),a=scenario(r.alternative,request.alternativeScenario.scenarioName),c=object(r.comparison);
 ensure(c.baselineScenarioName===request.baselineScenario.scenarioName&&c.alternativeScenarioName===request.alternativeScenario.scenarioName);
 for(const k of ['peakFundingDifference','closingLiquidityDifference','totalInflowsDifference','totalOutflowsDifference'])ensure(fundingAmount(c[k]));
 for(const k of ['firstDeficitDateChangeDays','recoveryDateChangeDays'])ensure(c[k]===null||Number.isInteger(c[k]));
 ensure(Array.isArray(c.dailyDifferences)&&c.dailyDifferences.length===b.wire.timeline.length);
 for(const[i,item]of(c.dailyDifferences as unknown[]).entries()){const d=object(item);ensure(d.date===b.wire.timeline[i].date&&d.baselineClosingPosition===b.wire.timeline[i].closingPosition&&d.alternativeClosingPosition===a.wire.timeline[i].closingPosition);for(const k of ['positionDifference','baselineDeficit','alternativeDeficit','deficitDifference'])ensure(fundingAmount(d[k]));}
 return {baseline:b.view,alternative:a.view,rows:b.wire.timeline.map((d,i)=>({date:d.date,baseline:d.closingPosition,alternative:a.wire.timeline[i].closingPosition})),opening:o.amount as string,source:source==='POSTED_ACTUAL'?'LEDGER':'MANUAL',asOf:r.asOfDate as string,evaluatedAt:p.calculatedAt as string,fundingDifference:c.peakFundingDifference as string,closingDifference:c.closingLiquidityDifference as string,warnings:r.warnings as string[],limitations:r.limitations as string[]};
}
export async function compareFundingScenarios(input:FundingEditorInput,signal?:AbortSignal):Promise<FundingComparisonView>{
 const payload=fundingPayload(input),raw=await apiClient<unknown>('/finance/funding/scenarios/compare',{method:'POST',body:JSON.stringify(payload),signal});
 if(signal?.aborted)throw new DOMException('Context changed.','AbortError');return parseFundingResponse(raw,payload);
}
export function fundingError(error:unknown):'forbidden'|'expired'|'invalid'|'unavailableLedger'|'malformed'|'error'{
 if(error instanceof InvalidFundingResponse)return 'malformed';
 if(error instanceof ApiError){if(error.status===403)return 'forbidden';if(error.status===401)return 'expired';if(error.status===400)return error.fieldErrors?.['openingLiquidity.source']?'unavailableLedger':'invalid';}return 'error';
}
export const fundingApi={compare:compareFundingScenarios};
