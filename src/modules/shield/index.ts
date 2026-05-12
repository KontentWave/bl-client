import type { ShieldModule } from "./types";

class ShieldStub implements ShieldModule {
  async getReadiness() {
    return Promise.resolve({
      status: "unknown" as const,
      requirements: ["Native shield module not connected yet."],
    });
  }

  async getLiveStatus() {
    return Promise.resolve({
      stage: "idle" as const,
      message: "Shield native integration pending.",
    });
  }
}

export const Shield: ShieldModule = new ShieldStub();
