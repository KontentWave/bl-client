export type ShieldReadiness = {
  status: "unknown" | "action_required" | "active" | "blocked";
  requirements: string[];
};

export type ShieldLiveStatus = {
  stage: "idle" | "monitoring" | "intercepting";
  message: string;
};

export interface ShieldModule {
  getReadiness(): Promise<ShieldReadiness>;
  getLiveStatus(): Promise<ShieldLiveStatus>;
}
