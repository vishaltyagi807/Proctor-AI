import {
  Activity,
  Bell,
  Building2,
  LayoutDashboard,
  MessageSquareWarning,
  PlusCircle,
  ScanFace,
  Send,
  ShieldCheck,
  SlidersHorizontal,
  UserRound,
  Users,
  type LucideIcon,
} from "lucide-react";
import type { Can } from "@/lib/permissions";

export type NavItem = {
  href: string;
  label: string;
  icon: LucideIcon;
  group: "Workspace" | "People" | "Configure" | "Account";
  visible: (can: Can) => boolean;
  keywords?: string;
};

export const NAV: NavItem[] = [
  { href: "/", label: "Overview", icon: LayoutDashboard, group: "Workspace", visible: () => true, keywords: "home dashboard" },
  { href: "/complaints", label: "Complaints", icon: MessageSquareWarning, group: "Workspace", visible: (can) => can("complaints", "read"), keywords: "issues tickets" },
  { href: "/complaints/new", label: "New complaint", icon: PlusCircle, group: "Workspace", visible: (can) => can("complaints", "write"), keywords: "file report create" },
  { href: "/notifications", label: "Notifications", icon: Bell, group: "Workspace", visible: () => true, keywords: "inbox alerts" },
  {
    href: "/faces",
    label: "Face recognition",
    icon: ScanFace,
    group: "Workspace",
    visible: (can) =>
      ["face_recognition", "face_live_recognition", "face_enrollments", "face_audit"].some((entity) => can(entity, "read")) || can("face_enrollments", "write") || can("face_import", "write"),
    keywords: "enroll identify camera biometric scan",
  },
  { href: "/users", label: "Users", icon: Users, group: "People", visible: (can) => can("users", "read", ["department", "all"]), keywords: "students teachers accounts" },
  { href: "/departments", label: "Departments", icon: Building2, group: "People", visible: (can) => can("departments", "read", ["department", "all"]), keywords: "faculty" },
  { href: "/roles", label: "Roles & access", icon: ShieldCheck, group: "People", visible: (can) => can("roles", "read", ["all"]), keywords: "permissions rbac" },
  {
    href: "/custom-fields",
    label: "Custom fields",
    icon: SlidersHorizontal,
    group: "Configure",
    visible: (can) => can("custom_fields", "write") || can("custom_fields", "update") || can("custom_fields", "delete"),
    keywords: "forms attributes",
  },
  {
    href: "/admin/notifications",
    label: "Broadcast & push",
    icon: Send,
    group: "Configure",
    visible: (can) => can("integrations", "read") || can("notifications", "write", ["all", "department"]),
    keywords: "fcm announce message",
  },
  {
    href: "/admin/system",
    label: "System monitor",
    icon: Activity,
    group: "Configure",
    visible: (can) => can("system_monitor", "read"),
    keywords: "cpu memory ram disk network health metrics resources performance server",
  },
  { href: "/profile", label: "My profile", icon: UserRound, group: "Account", visible: () => true, keywords: "settings account" },
];
