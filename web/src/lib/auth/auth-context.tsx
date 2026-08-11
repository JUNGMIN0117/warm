"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { clearAuth, loadAuth, saveAuth } from "./token-storage";
import type { StoredAuth } from "./token-storage";
import type { AuthResponse } from "@/lib/api/types";

/**
 * 로그인 상태의 단일 출처.
 *
 * SSR 첫 렌더에서는 localStorage를 읽을 수 없으므로 항상 "익명"으로
 * 그리고, 마운트 후 저장된 세션을 복원한다. status에 "loading"을 두는
 * 이유: 복원 전에 이력 페이지가 "로그인하세요"를 깜빡 보여주는 것을
 * 막기 위해서다 (hydration mismatch 방지).
 */

type AuthStatus = "loading" | "anonymous" | "authenticated";

interface AuthContextValue {
  status: AuthStatus;
  auth: StoredAuth | null;
  signIn: (response: AuthResponse) => void;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [auth, setAuth] = useState<StoredAuth | null>(null);

  useEffect(() => {
    const stored = loadAuth();
    setAuth(stored);
    setStatus(stored === null ? "anonymous" : "authenticated");
  }, []);

  const signIn = useCallback((response: AuthResponse) => {
    const stored: StoredAuth = {
      accessToken: response.accessToken,
      expiresAt: response.expiresAt,
      userId: response.userId,
      displayName: response.displayName,
    };
    saveAuth(stored);
    setAuth(stored);
    setStatus("authenticated");
  }, []);

  const signOut = useCallback(() => {
    clearAuth();
    setAuth(null);
    setStatus("anonymous");
  }, []);

  const value = useMemo(
    () => ({ status, auth, signIn, signOut }),
    [status, auth, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (context === null) {
    throw new Error("useAuth는 AuthProvider 안에서만 쓸 수 있습니다.");
  }
  return context;
}
