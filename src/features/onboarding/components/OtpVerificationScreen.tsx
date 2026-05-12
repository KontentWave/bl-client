import { useMemo, useState } from "react";
import { Link, useRouter } from "expo-router";
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";

import { verifyAuth } from "@/api/auth";
import { ApiError } from "@/api/client";
import { useAuthFlow } from "@/features/onboarding/state/AuthFlowContext";
import { ScreenShell } from "@/features/shared/components/ScreenShell";
import { createAuthVerifySignature } from "@/security/signatures";
import { colors } from "@/theme/colors";
import { spacing } from "@/theme/spacing";

export function OtpVerificationScreen() {
  const router = useRouter();
  const { challenge, setVerifiedSession } = useAuthFlow();
  const [otp, setOtp] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const canSubmit = useMemo(
    () => Boolean(challenge) && otp.trim().length === 6 && !isSubmitting,
    [challenge, isSubmitting, otp],
  );

  async function handleVerify() {
    if (!challenge || !canSubmit) {
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      const { publicKey, signature } = await createAuthVerifySignature(
        challenge.challengeId,
      );
      const response = await verifyAuth({
        challenge_id: challenge.challengeId,
        otp: otp.trim(),
        public_key: publicKey,
        signature,
      });

      setVerifiedSession({
        challengeId: response.challenge_id,
        maskedPhoneNumber: response.masked_phone_number,
        verifiedAt: response.verified_at,
      });
      router.replace("/verified");
    } catch (error) {
      if (error instanceof ApiError) {
        const fieldMessage =
          error.errors.otp?.[0] ??
          error.errors.signature?.[0] ??
          error.errors.challenge_id?.[0];
        setErrorMessage(fieldMessage ?? error.message);
      } else if (error instanceof Error) {
        setErrorMessage(error.message);
      } else {
        setErrorMessage("Unable to verify OTP.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <ScreenShell
      eyebrow="Auth"
      title="Enter the OTP"
      body="This screen now uses the live Laravel auth endpoints. OTP verification still depends on the native CryptoVault module for the real hardware-backed key and signature."
    >
      {challenge ? (
        <View style={styles.summary}>
          <Text style={styles.summaryLabel}>Masked phone</Text>
          <Text style={styles.summaryValue}>{challenge.maskedPhoneNumber}</Text>
          <Text style={styles.summaryMeta}>
            Server authoritative expiry: {challenge.otpExpiresAt}
          </Text>
        </View>
      ) : (
        <View style={styles.summary}>
          <Text style={styles.summaryValue}>No active challenge</Text>
          <Text style={styles.summaryMeta}>
            Start again from the ad URL screen to request a fresh OTP.
          </Text>
        </View>
      )}
      <TextInput
        keyboardType="number-pad"
        maxLength={6}
        onChangeText={setOtp}
        placeholder="123456"
        placeholderTextColor={colors.muted}
        style={styles.input}
        value={otp}
      />
      {errorMessage ? (
        <Text style={styles.errorText}>{errorMessage}</Text>
      ) : null}
      <Pressable
        disabled={!canSubmit}
        onPress={handleVerify}
        style={[styles.button, !canSubmit && styles.buttonDisabled]}
      >
        {isSubmitting ? (
          <ActivityIndicator color="#fff7ed" />
        ) : (
          <Text style={styles.buttonText}>Verify with device signature</Text>
        )}
      </Pressable>
      <Link href="/onboarding" asChild>
        <Pressable style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Start a new challenge</Text>
        </Pressable>
      </Link>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  summary: {
    backgroundColor: colors.surfaceStrong,
    borderRadius: 20,
    gap: spacing.xs,
    padding: spacing.md,
  },
  summaryLabel: {
    color: colors.muted,
    fontSize: 14,
    textTransform: "uppercase",
  },
  summaryValue: {
    color: colors.text,
    fontSize: 22,
    fontWeight: "700",
  },
  summaryMeta: {
    color: colors.text,
    fontSize: 14,
  },
  input: {
    backgroundColor: "#ffffff",
    borderColor: colors.border,
    borderRadius: 16,
    borderWidth: 1,
    color: colors.text,
    fontSize: 20,
    letterSpacing: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    textAlign: "center",
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
    justifyContent: "center",
    minHeight: 52,
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
  secondaryButton: {
    alignItems: "center",
    paddingVertical: spacing.sm,
  },
  secondaryButtonText: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "600",
  },
});
