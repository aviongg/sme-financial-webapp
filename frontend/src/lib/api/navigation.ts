import { Permission } from './session';
export function routePermission(path: string): Permission | null {
    if (path === '/records/new')
        return 'RECORD_CREATE_UPDATE';
    if (path === '/scan')
        return 'DOCUMENT_UPLOAD';
    if (path === '/upload' || path.startsWith('/upload/'))
        return 'DOCUMENT_READ';
    if (path === '/sharia-zakat')
        return 'ZAKAT_READ_CALCULATE';
    if (path === '/settings')
        return null;
    return 'FINANCIAL_DATA_READ';
}
