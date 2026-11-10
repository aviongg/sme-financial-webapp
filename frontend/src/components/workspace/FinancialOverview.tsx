"use client";
import { useEffect,useState } from 'react';
import Link from 'next/link';
import { useSession } from '@/components/auth/SessionProvider';
import { useConfiguration } from '@/components/configuration/ConfigurationProvider';
import { useLanguage } from '@/lib/i18n/context';
import { dashboardFinanceCopy } from '@/lib/i18n/translations/dashboard-finance';
import { dashboardFinanceApi,exactDisplayAmount,formatDashboardAmount,projectProfitability } from '@/lib/api/dashboard-finance';
import { loadPeriodTotals } from '@/lib/api/dev-a/transaction-totals';
import { obligationsApi } from '@/lib/api/dev-a/obligations';
import { cashForecastApi } from '@/lib/api/dev-a/cash-forecast';
import { financialRiskApi } from '@/lib/api/dev-a/financial-risk';
import { projectsApi } from '@/lib/api/projects';
import { projectActualsApi } from '@/lib/api/projectActuals';
import { riskEn, riskUr } from '@/components/records/risk/risk-copy';
import { ApiError } from '@/lib/api/client';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import type { ReactNode } from 'react';
type Resource<T>={loading:boolean;data:T|null;error:unknown};
function useEvidence<T>(load:(signal:AbortSignal)=>Promise<T>,key:string):Resource<T>{
 const [state,setState]=useState<Resource<T>&{key:string}>({key,loading:true,data:null,error:null});
 useEffect(()=>{const c=new AbortController();queueMicrotask(()=>{if(!c.signal.aborted)setState({key,loading:true,data:null,error:null});});
 load(c.signal).then(data=>{if(!c.signal.aborted)setState({key,loading:false,data,error:null});}).catch(error=>{if(!c.signal.aborted)setState({key,loading:false,data:null,error});});return()=>c.abort();
 // Callers supply every context/filter revision through key; language changes preserve authorized state.
 // eslint-disable-next-line react-hooks/exhaustive-deps
 },[key]);return state.key===key?state:{loading:true,data:null,error:null};
}
export function FinancialOverview({preview=false}:{preview?:boolean}){
 const session=useSession(),{locale}=useLanguage(),copy=dashboardFinanceCopy(locale);
 const key=JSON.stringify([session.epoch,session.user?.id,session.business?.businessId,session.business?.role,session.status]);
 if(preview)return <Card><h2 className="font-semibold">{copy.title}</h2><p className="text-sm">{copy.preview}</p></Card>;
 return session.status==='authenticated'&&session.can('FINANCIAL_DATA_READ')?<LiveFinancialOverview key={key} businessId={session.business!.businessId}/>:null;
}
function LiveFinancialOverview({businessId}:{businessId:string}){
 const {locale}=useLanguage(),copy=dashboardFinanceCopy(locale),configuration=useConfiguration();
 const [attempt,setAttempt]=useState(0),[projectId,setProjectId]=useState('');
 const [period]=useState(()=>{const end=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Karachi',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());const start=new Date(end+'T00:00:00Z');start.setUTCDate(start.getUTCDate()-29);return {startDate:start.toISOString().slice(0,10),endDate:end};});
 const cash=useEvidence(s=>dashboardFinanceApi.cash(businessId,s),String(attempt));
 const totals=useEvidence(s=>loadPeriodTotals(period,s),String(attempt));
 const aging=useEvidence(s=>obligationsApi.getAging(undefined,s),String(attempt));
 const forecast=useEvidence(s=>cashForecastApi.getDailyForecast({currency:'PKR',horizonDays:30},s),String(attempt));
 const risk=useEvidence(s=>financialRiskApi.getSummary({currency:'PKR',horizonDays:30},s),String(attempt));
 const projectsAllowed=configuration?.available('projects')===true;
 const projects=useEvidence(s=>projectsAllowed?projectsApi.list({},s):Promise.resolve(null),attempt+':'+projectsAllowed);
 const costing=useEvidence(s=>projectId?projectActualsApi.getCostingSummary(projectId,s):Promise.resolve(null),attempt+':'+projectId);
 const project=useEvidence(s=>projectId?projectActualsApi.getFinancialOverview(projectId,s):Promise.resolve(null),attempt+':'+projectId);
 const amount=(value:unknown,currency='PKR',scale=2)=>{const exact=exactDisplayAmount(value,scale);return exact===null?<span>{copy.unavailable}</span>:<bdi dir="ltr">{formatDashboardAmount(exact)} {currency}</bdi>;};
 function panel<T>(resource:Resource<T>,render:(data:NonNullable<T>)=>ReactNode):ReactNode{return resource.loading?<p role="status">{copy.loading}</p>:resource.error?<p role="alert">{resource.error instanceof ApiError&&resource.error.status===403?copy.forbidden:copy.unavailable}</p>:resource.data===null?<p>{copy.unavailable}</p>:render(resource.data as NonNullable<T>);}
 const item=(label:string,value:ReactNode)=><div className="min-w-0"><dt className="text-xs text-[var(--color-text-secondary)]">{label}</dt><dd className="mt-1 font-semibold break-words">{value}</dd></div>;
 const link=(href:string)=><Link href={href} className="text-sm underline text-[var(--color-brand-primary)]">{copy.details}</Link>;
 return <section aria-label={copy.title} className="min-w-0 space-y-4" data-testid="dashboard-live-finance">
 <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold">{copy.title}</h2><Button size="sm" variant="secondary" onClick={()=>setAttempt(n=>n+1)}>{copy.retry}</Button></div>
 <div className="grid min-w-0 gap-4 lg:grid-cols-2">
 <Card className="min-w-0 space-y-3"><h3 className="font-semibold">{copy.cash}</h3><p className="text-xs">{copy.posted} · {copy.cashHelp}</p>{panel(cash,data=>data.status==='UNAVAILABLE'?<p>{copy.unavailable}</p>:data.data.length?<><dl className="space-y-3">{data.data.map(row=><div key={row.currency}>{item(row.currency,amount(row.currentBalance,row.currency,row.currencyDecimals))}</div>)}</dl><p className="text-xs">{copy.source}: <bdi dir="ltr">{data.asOf}</bdi></p>{(data.completeness!=='COMPLETE'||data.warnings.length>0)&&<p role="status" className="text-xs">{copy.partialEvidence} {copy.partial}</p>}</>:<p>{copy.emptyCash}</p>)}{link('/finance/accounts')}</Card>
 <Card className="min-w-0 space-y-3"><h3 className="font-semibold">{copy.activity}</h3><p className="text-xs">{copy.posted} · <bdi dir="ltr">{period.startDate} — {period.endDate}</bdi></p>{panel(totals,data=><dl className="grid gap-3">{item(copy.received,amount(data.moneyReceived))}{item(copy.paid,amount(data.moneyPaid))}{item(copy.movement,amount(data.netCashMovement))}</dl>)}<p className="text-xs">{copy.notProfit}</p>{link('/records?view=activity')}</Card>
 <Card className="min-w-0 space-y-3"><h3 className="font-semibold">{copy.obligations}</h3>{panel(aging,data=><dl className="grid gap-3">{item(copy.receivables,amount(data.receivables?.totalOutstanding))}{item(copy.payables,amount(data.payables?.totalOutstanding))}</dl>)}{link('/records/receivables')}</Card>
 <Card className="min-w-0 space-y-3"><h3 className="font-semibold">{copy.forecastTitle}</h3><p className="text-xs">{copy.forecast}</p>{panel(forecast,data=><dl className="grid gap-3">{item(copy.closing,amount(data.summary.endingProjectedCash))}{item(copy.funding,amount(data.summary.maximumExpectedShortfall))}{item(copy.first,data.summary.firstDeficitDate?<bdi dir="ltr">{data.summary.firstDeficitDate}</bdi>:copy.none)}</dl>)}<p className="text-xs">{copy.partial}</p><div className="flex flex-wrap gap-3">{link('/finance/forecast')}<Link className="text-sm underline" href="/finance/funding">{copy.fundingLink}</Link></div></Card>
 <Card className="space-y-3"><h3 className="font-semibold">{copy.risks}</h3>{panel(risk,data=><p><bdi dir="ltr">{(locale==='ur'?riskUr:riskEn)[('sev_'+data.overallSeverity) as 'sev_CRITICAL']} · {data.assessedCategoriesCount}/{data.totalCategories}</bdi></p>)}{link('/finance/risks')}</Card>
 {projectsAllowed&&<Card className="min-w-0 space-y-3"><h3 className="font-semibold">{copy.projects}</h3>{panel(projects,data=><Select label={copy.chooseProject} value={projectId} onChange={e=>setProjectId(e.target.value)} options={[{value:'',label:data.items.length?copy.chooseProject:copy.noProjects},...data.items.map(p=>({value:p.id,label:p.name}))]}/>)}{projectId&&panel(project,data=><><dl className="grid gap-3">{item(copy.actualIn,amount(data.attributedCashInflows,data.baseCurrency))}{item(copy.actualOut,amount(data.attributedCashOutflows,data.baseCurrency))}{item(copy.commitments,amount(data.totalOutstandingCommitments,data.baseCurrency))}{item(copy.unposted,amount(data.unpostedCommercialBills,data.baseCurrency))}</dl><p className="text-xs">{copy.projectHelp}</p>{panel(costing,cost=>projectProfitability(cost)!==null?<dl>{item(copy.profit,amount(projectProfitability(cost),cost.baseCurrency))}</dl>:<p className="text-sm">{copy.unknownCogs}</p>)}{link('/projects/'+projectId)}</>)}<p className="text-xs">{copy.plan}</p></Card>}
 </div>
 <div className="flex flex-wrap gap-4 text-xs text-[var(--color-text-muted)] pt-2 border-t border-[var(--color-border-subtle)]">
  <div className="flex items-center gap-1.5">
   <span>{copy.recognition}:</span>
   <Link href="/finance/recognition" className="underline text-[var(--color-brand-primary)] font-medium">
    {locale === 'ur' ? 'اندراج ورک اسپیس' : 'Recognition Workspace'}
   </Link>
  </div>
  <div className="flex items-center gap-1.5">
   <span>{copy.healthHelp}</span>
   <Link href="/records/bridge" className="underline text-[var(--color-brand-primary)] font-medium">
    {locale === 'ur' ? 'لیجر برج' : 'Ledger Bridge'}
   </Link>
  </div>
 </div>
 </section>;
}
