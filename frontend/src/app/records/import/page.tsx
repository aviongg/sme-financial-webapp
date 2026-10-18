"use client";
import { LivePage } from '@/components/integration/LivePage';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { isDemoMode } from '@/lib/api/config';
import { ImportPreview } from '@/components/records/import/ImportPreview';
import { importEn, importUr } from '@/components/records/import/import-copy';

export default function RecordImportPage() {
 const session = useSession(), { locale } = useLanguage(), copy = locale === 'ur' ? importUr : importEn;
 // Keyed private subtree is destroyed on every identity/business/permission transition.
 const context = JSON.stringify([session.epoch, session.user?.id, session.business?.businessId, session.business?.role, session.business?.membershipStatus, session.status]);
 return <LivePage title={copy.title}>{isDemoMode ? <ImportPreview key="demo-preview" demo /> : session.status === 'authenticated' && session.business?.membershipStatus === 'ACTIVE' ? <ImportPreview key={context} /> : <p role="status">{copy.session}</p>}</LivePage>;
}
