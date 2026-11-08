export type AuthStage = 'FULLY_AUTHENTICATED' | 'PASSWORD_CHANGE_REQUIRED' | 'MFA_ENROLLMENT_REQUIRED' | 'MFA_CHALLENGE_REQUIRED';
export interface AuthUser {
    id: string;
    email: string;
    fullName: string;
    authStage: AuthStage;
    mustChangePassword: boolean;
    accountStatus: string;
    platformRole: string | null;
}
export type Role = 'OWNER' | 'ACCOUNTANT' | 'MANAGER' | 'VIEWER' | 'SALESPERSON';
export interface BusinessSummary {
    businessName: string;
    businessId: string;
    businessType: string;
    languagePreference: 'en' | 'ur';
    role: Role;
    membershipStatus: string;
    active: boolean;
}
export type Permission = 'FINANCIAL_DATA_READ' | 'RECORD_CREATE_UPDATE' | 'DOCUMENT_UPLOAD' | 'DOCUMENT_READ' | 'DOCUMENT_CONFIRM' | 'DOCUMENT_DELETE' | 'DOCUMENT_EDIT' | 'SCORE_CALCULATE' | 'ZAKAT_READ_CALCULATE' | 'BUSINESS_SETTINGS_MANAGE' | 'WHATSAPP_CONFIG_MANAGE' | 'MEMBERSHIP_MANAGE' | 'SALESPERSON_MANAGE' | 'COMMISSION_MANAGE' | 'COMMISSION_READ';
const permissions: Record<Role, readonly Permission[]> = {
    OWNER: ['FINANCIAL_DATA_READ', 'RECORD_CREATE_UPDATE', 'DOCUMENT_UPLOAD', 'DOCUMENT_READ', 'DOCUMENT_CONFIRM', 'DOCUMENT_DELETE', 'DOCUMENT_EDIT', 'SCORE_CALCULATE', 'ZAKAT_READ_CALCULATE', 'BUSINESS_SETTINGS_MANAGE', 'WHATSAPP_CONFIG_MANAGE', 'MEMBERSHIP_MANAGE', 'SALESPERSON_MANAGE', 'COMMISSION_MANAGE', 'COMMISSION_READ'],
    ACCOUNTANT: ['FINANCIAL_DATA_READ', 'RECORD_CREATE_UPDATE', 'DOCUMENT_UPLOAD', 'DOCUMENT_READ', 'DOCUMENT_CONFIRM', 'DOCUMENT_DELETE', 'DOCUMENT_EDIT', 'SCORE_CALCULATE', 'ZAKAT_READ_CALCULATE', 'SALESPERSON_MANAGE', 'COMMISSION_MANAGE', 'COMMISSION_READ'],
    MANAGER: ['FINANCIAL_DATA_READ', 'DOCUMENT_UPLOAD', 'DOCUMENT_READ', 'ZAKAT_READ_CALCULATE', 'COMMISSION_READ'],
    VIEWER: ['FINANCIAL_DATA_READ'],
    SALESPERSON: ['COMMISSION_READ'],
};
/** Presentation only; every endpoint independently enforces authorization. */
export function hasPermission(business: BusinessSummary | null, permission: Permission) {
    return business?.membershipStatus === 'ACTIVE' && !!permissions[business.role]?.includes(permission);
}

/** Only a security-context change warrants discarding private forms and results. */
export function sameSessionBoundary(previousUser: AuthUser | null, user: AuthUser, previousBusiness: BusinessSummary | null, business: BusinessSummary | null): boolean {
 if (!previousUser || ['id', 'authStage', 'accountStatus', 'mustChangePassword', 'platformRole'].some(key => previousUser[key as keyof AuthUser] !== user[key as keyof AuthUser])) return false;
 if (!previousBusiness || !business) return previousBusiness === business;
 return ['businessId', 'role', 'membershipStatus', 'businessType', 'active'].every(key => previousBusiness[key as keyof BusinessSummary] === business[key as keyof BusinessSummary]);
}
