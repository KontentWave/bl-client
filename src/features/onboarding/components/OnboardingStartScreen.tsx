import { useMemo, useState } from "react";
import { useRouter } from "expo-router";
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";

import { ApiError } from "@/api/client";
import { initiateAuth } from "@/api/auth";
import { appEnv } from "@/config/env";
import { ScreenShell } from "@/features/shared/components/ScreenShell";
import { useAuthFlow } from "@/features/onboarding/state/AuthFlowContext";
import { colors } from "@/theme/colors";
import { spacing } from "@/theme/spacing";

export function OnboardingStartScreen() {
  const router = useRouter();
  const { setChallenge, setVerifiedSession } = useAuthFlow();
  const [adUrl, setAdUrl] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const isEnabled = useMemo(() => adUrl.trim().length > 0, [adUrl]);

  async function handleStartVerification() {
    if (!isEnabled || isSubmitting) {
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      const response = await initiateAuth({ ad_url: adUrl.trim() });

      setVerifiedSession(null);
      setChallenge({
        challengeId: response.challenge_id,
        maskedPhoneNumber: response.masked_phone_number,
        otpExpiresAt: response.otp_expires_at,
      });
      router.push("/onboarding/otp");
    } catch (error) {
      if (error instanceof ApiError) {
        const fieldMessage = error.errors.ad_url?.[0];
        setErrorMessage(fieldMessage ?? error.message);
      } else if (error instanceof Error) {
        setErrorMessage(error.message);
      } else {
        setErrorMessage("Unable to start verification.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <ScreenShell
      eyebrow="Phase 6"
      title="Verify a worker ad"
      body="This foundation keeps the Laravel contract intact: the client only submits an ad URL, then transitions to OTP verification using the masked phone number returned by the backend."
    >
      <View style={styles.group}>
        <Text style={styles.label}>Escort ad URL</Text>
        <TextInput
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="url"
          onChangeText={setAdUrl}
          placeholder="https://amaterky.sk/..."
          placeholderTextColor={colors.muted}
          style={styles.input}
          value={adUrl}
        />
        <Text style={styles.helper}>
          Default API target: {appEnv.apiBaseUrl}
        </Text>
      </View>
      {errorMessage ? (
        <Text style={styles.errorText}>{errorMessage}</Text>
      ) : null}
      <Pressable
        disabled={!isEnabled || isSubmitting}
        onPress={handleStartVerification}
        style={[
          styles.button,
          (!isEnabled || isSubmitting) && styles.buttonDisabled,
        ]}
      >
        {isSubmitting ? (
          <ActivityIndicator color="#fff7ed" />
        ) : (
          <Text style={styles.buttonText}>Start live verification</Text>
        )}
      </Pressable>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  group: {
    gap: spacing.sm,
  },
  label: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "700",
  },
  input: {
    backgroundColor: "#ffffff",
    borderColor: colors.border,
    borderRadius: 16,
    borderWidth: 1,
    color: colors.text,
    fontSize: 16,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
  },
  helper: {
    color: colors.muted,
    fontSize: 14,
  },
  errorText: {
    color: "#b91c1c",
    fontSize: 14,
    lineHeight: 20,
  },
  button: {
    alignItems: "center",
    backgroundColor: colors.accent,
    borderRadius: 999,
    minHeight: 52,
    justifyContent: "center",
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonText: {
    color: "#fff7ed",
    fontSize: 16,
    fontWeight: "700",
  },
});
