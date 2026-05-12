import type { ExpoConfig } from "expo/config";

const config: ExpoConfig = {
  name: "Blacklist Client",
  slug: "blacklist-client",
  scheme: "blacklistclient",
  version: "1.0.0",
  orientation: "portrait",
  userInterfaceStyle: "light",
  newArchEnabled: true,
  plugins: ["expo-router", "expo-dev-client"],
  experiments: {
    typedRoutes: true,
  },
  android: {
    edgeToEdgeEnabled: true,
    package: "com.blacklist.client",
  },
  ios: {
    supportsTablet: false,
    bundleIdentifier: "com.blacklist.client",
  },
  extra: {
    apiBaseUrl: "https://bcuszlr92817.zafo-forum.sk/api",
  },
};

export default config;
