/** Demo mode is an explicit build-time choice; live requests never use mock fallbacks. */
export const isDemoMode = process.env.NEXT_PUBLIC_DATA_MODE === "demo";
