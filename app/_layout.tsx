import { Stack } from "expo-router";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";

import { AuthFlowProvider } from "@/features/onboarding/state/AuthFlowContext";
import { AppThemeProvider } from "@/theme/provider";

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <AuthFlowProvider>
        <AppThemeProvider>
          <StatusBar style="dark" />
          <Stack
            screenOptions={{
              headerShadowVisible: false,
              headerStyle: { backgroundColor: "#f4efe6" },
              contentStyle: { backgroundColor: "#f4efe6" },
              headerTintColor: "#1f2937",
            }}
          >
            <Stack.Screen name="index" options={{ headerShown: false }} />
            <Stack.Screen
              name="onboarding/index"
              options={{ title: "Verify Ad" }}
            />
            <Stack.Screen
              name="onboarding/otp"
              options={{ title: "Enter OTP" }}
            />
            <Stack.Screen
              name="verified/index"
              options={{ title: "Dashboard" }}
            />
            <Stack.Screen
              name="verified/reporting"
              options={{ title: "Report Worker" }}
            />
            <Stack.Screen
              name="verified/query"
              options={{ title: "Check Blacklist" }}
            />
          </Stack>
        </AppThemeProvider>
      </AuthFlowProvider>
    </SafeAreaProvider>
  );
}
