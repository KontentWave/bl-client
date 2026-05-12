import Constants from "expo-constants";

import { HOSTED_API_BASE_URL } from "./urls";

type ExpoExtra = {
  apiBaseUrl?: string;
};

const extra = (Constants.expoConfig?.extra ?? {}) as ExpoExtra;

export const appEnv = {
  apiBaseUrl: extra.apiBaseUrl ?? HOSTED_API_BASE_URL,
};
