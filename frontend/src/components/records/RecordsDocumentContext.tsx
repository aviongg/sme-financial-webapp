"use client";
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useLanguage } from '@/lib/i18n/context';
import { safeRecordsReturn } from './record-period';
import { recordsEn, recordsUr } from './records-copy';
export function RecordsDocumentContext() {
 const params = useSearchParams(), {locale} = useLanguage(), copy = locale === 'ur' ? recordsUr : recordsEn;
 if (!params.has('returnTo')) return null;
 return <div className="space-y-2"><Link className="underline" href={safeRecordsReturn(params.get('returnTo'))}>{copy.back}</Link><p>{copy.ocrNotice}</p></div>;
}
