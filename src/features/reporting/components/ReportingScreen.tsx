import { StyleSheet, Text, View } from "react-native";

import { ScreenShell } from "@/features/shared/components/ScreenShell";
import { colors } from "@/theme/colors";
import { spacing } from "@/theme/spacing";

export function ReportingScreen() {
  return (
    <ScreenShell
      eyebrow="Reports"
      title="Zero-knowledge reporting"
      body="The reporting client is already pinned to the Laravel contract for POST /reports: client phone number, feature, public key, and signature."
    >
      <View style={styles.panel}>
        <Text style={styles.title}>Canonical payload fields</Text>
        <Text style={styles.item}>client_phone_number</Text>
        <Text style={styles.item}>feature</Text>
        <Text style={styles.item}>public_key</Text>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  panel: {
    gap: spacing.sm,
  },
  title: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "700",
  },
  item: {
    color: colors.muted,
    fontSize: 16,
  },
});
