import { Link } from "expo-router";
import { Pressable, StyleSheet, Text, View } from "react-native";

import { useAuthFlow } from "@/features/onboarding/state/AuthFlowContext";
import { Shield } from "@/modules/shield";
import { ScreenShell } from "@/features/shared/components/ScreenShell";
import { colors } from "@/theme/colors";
import { spacing } from "@/theme/spacing";

export function VerifiedHomeScreen() {
  const { verifiedSession } = useAuthFlow();
  void Shield.getReadiness();

  return (
    <ScreenShell
      eyebrow="Verified"
      title="Worker dashboard"
      body="This shell preserves the old app split: verified identity summary, Shield readiness, reporting, and blacklist lookups live behind the post-verification surface."
    >
      <View style={styles.panel}>
        <Text style={styles.panelTitle}>Identity status</Text>
        <Text style={styles.panelValue}>
          Verified for {verifiedSession?.maskedPhoneNumber ?? "unknown worker"}
        </Text>
        <Text style={styles.panelMeta}>
          Verified at {verifiedSession?.verifiedAt ?? "not yet available"}
        </Text>
      </View>
      <View style={styles.row}>
        <Link href="/verified/reporting" asChild>
          <Pressable style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>Open reporting</Text>
          </Pressable>
        </Link>
        <Link href="/verified/query" asChild>
          <Pressable style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>Open query</Text>
          </Pressable>
        </Link>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  panel: {
    backgroundColor: colors.surfaceStrong,
    borderRadius: 20,
    gap: spacing.xs,
    padding: spacing.md,
  },
  panelTitle: {
    color: colors.muted,
    fontSize: 14,
    textTransform: "uppercase",
  },
  panelValue: {
    color: colors.text,
    fontSize: 20,
    fontWeight: "700",
  },
  panelMeta: {
    color: colors.muted,
    fontSize: 14,
  },
  row: {
    flexDirection: "row",
    gap: spacing.md,
  },
  secondaryButton: {
    backgroundColor: "#ffffff",
    borderColor: colors.border,
    borderRadius: 18,
    borderWidth: 1,
    flex: 1,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
  },
  secondaryButtonText: {
    color: colors.text,
    fontSize: 16,
    fontWeight: "700",
    textAlign: "center",
  },
});
