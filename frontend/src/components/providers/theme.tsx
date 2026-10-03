import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

type Mode = "light" | "dark" | "system";
type ThemeContextValue = { mode: Mode; resolved: "light" | "dark"; setMode: (mode: Mode) => void };

export const THEME_STORAGE_KEY = "pai_theme";

const ThemeContext = createContext<ThemeContextValue | null>(null);

function systemDark() {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

function readMode(): Mode {
  const value = document.documentElement.dataset.theme;
  return value === "dark" || value === "light" ? value : "system";
}

function apply(mode: Mode) {
  const dark = mode === "dark" || (mode === "system" && systemDark());
  const root = document.documentElement;
  root.classList.add("theme-switching");
  root.classList.toggle("dark", dark);
  root.dataset.theme = mode;
  window.setTimeout(() => root.classList.remove("theme-switching"), 300);
  try {
    localStorage.setItem(THEME_STORAGE_KEY, mode);
  } catch {
    return dark;
  }
  return dark;
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [mode, setModeState] = useState<Mode>(readMode);
  const [dark, setDark] = useState(() => document.documentElement.classList.contains("dark"));

  const setMode = useCallback((next: Mode) => {
    setModeState(next);
    setDark(apply(next));
  }, []);

  useEffect(() => {
    if (mode !== "system") return;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => setDark(apply("system"));
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, [mode]);

  const value = useMemo(() => ({ mode, resolved: dark ? ("dark" as const) : ("light" as const), setMode }), [mode, dark, setMode]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useTheme must be used inside ThemeProvider");
  return context;
}
