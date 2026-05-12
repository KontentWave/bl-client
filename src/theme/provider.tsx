import { PropsWithChildren } from "react";
import { View } from "react-native";

import { colors } from "./colors";

export function AppThemeProvider({ children }: PropsWithChildren) {
  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      {children}
    </View>
  );
}
