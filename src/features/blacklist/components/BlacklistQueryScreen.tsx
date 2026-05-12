import { StyleSheet, Text, View } from "react-native";

import { ScreenShell } from "@/features/shared/components/ScreenShell";
import { colors } from "@/theme/colors";
import { spacing } from "@/theme/spacing";

export function BlacklistQueryScreen() {
  return (
    <ScreenShell
      eyebrow="Blacklist"
      title="Check Level 2 features"
      body="This route is wired for the Laravel POST /blacklist/check envelope, with target hash plus hardware-bound signature as the contract boundary."
    >
      <View style={styles.panel}>
        <Text style={styles.title}>Expected request shape</Text>
        <Text style={styles.item}>target_hash</Text>
        <Text style={styles.item}>public_key</Text>
        <Text style={styles.item}>signature</Text>
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
