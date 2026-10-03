import { getRouteApi } from "@tanstack/react-router";
import { LoginView } from "@/components/auth/login-view";
import { safeNext } from "@/lib/session";

const loginApi = getRouteApi("/login");

export function LoginPage() {
  const { next, expired } = loginApi.useSearch();
  return <LoginView next={safeNext(next)} expired={expired === true} />;
}
