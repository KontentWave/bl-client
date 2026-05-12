import {
  createContext,
  PropsWithChildren,
  useContext,
  useMemo,
  useState,
} from "react";

type AuthChallenge = {
  challengeId: string;
  maskedPhoneNumber: string;
  otpExpiresAt: string;
};

type VerifiedSession = {
  challengeId: string;
  maskedPhoneNumber: string;
  verifiedAt: string;
};

type AuthFlowContextValue = {
  challenge: AuthChallenge | null;
  verifiedSession: VerifiedSession | null;
  setChallenge: (challenge: AuthChallenge | null) => void;
  setVerifiedSession: (session: VerifiedSession | null) => void;
  reset: () => void;
};

const AuthFlowContext = createContext<AuthFlowContextValue | null>(null);

export function AuthFlowProvider({ children }: PropsWithChildren) {
  const [challenge, setChallenge] = useState<AuthChallenge | null>(null);
  const [verifiedSession, setVerifiedSession] =
    useState<VerifiedSession | null>(null);

  const value = useMemo<AuthFlowContextValue>(
    () => ({
      challenge,
      verifiedSession,
      setChallenge,
      setVerifiedSession,
      reset: () => {
        setChallenge(null);
        setVerifiedSession(null);
      },
    }),
    [challenge, verifiedSession],
  );

  return (
    <AuthFlowContext.Provider value={value}>
      {children}
    </AuthFlowContext.Provider>
  );
}

export function useAuthFlow() {
  const context = useContext(AuthFlowContext);

  if (!context) {
    throw new Error("useAuthFlow must be used within an AuthFlowProvider.");
  }

  return context;
}
