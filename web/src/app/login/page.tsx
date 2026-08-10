import { AuthForm } from "@/components/auth/auth-form";

export const metadata = { title: "로그인 — Personal Color AI" };

export default function LoginPage() {
  return <AuthForm mode="login" />;
}
