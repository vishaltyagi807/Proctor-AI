import type { Action, Role, Scope, Session } from "./types";

export type Can = (entity: string, action: Action, scopes?: Scope[]) => boolean;

export const LOWEST_LEVEL = Number.MAX_SAFE_INTEGER;

export type Relation = "self" | "senior" | "same_role" | "parallel_role" | "junior";

export type ManageTarget = {
  id: string;
  roles?: Pick<Role, "id" | "level">[] | null;
  departments?: { id: string }[] | null;
};

export type ManagedRole = Pick<Role, "id" | "level" | "superuser">;

export type Manage = {
  level: number;
  selfId: string;
  relation: (target: ManageTarget) => Relation;
  roleRelation: (role: Pick<Role, "id" | "level">) => Relation;
  view: (target: ManageTarget) => boolean;
  user: (target: ManageTarget, action: Action) => boolean;
  rolesOf: (target: ManageTarget, action: Action) => boolean;
  role: (role: ManagedRole, action: Action) => boolean;
  minLevelFor: (role: ManagedRole | null) => number;
  minRoleLevel: number;
};

export const RELATION_ENTITY: Record<"senior" | "same_role" | "parallel_role", string> = {
  senior: "senior_users",
  same_role: "same_role_peers",
  parallel_role: "parallel_role_peers",
};

export const RELATION_LABEL: Record<Relation, string> = {
  self: "You",
  senior: "Ranked above you",
  same_role: "Shares your role",
  parallel_role: "Parallel role",
  junior: "Ranked below you",
};

export function makeCan(session: Session): Can {
  const superuser = session.roles.some((role) => role.superuser);
  return (entity, action, scopes) => {
    if (superuser) return true;
    return session.permissions.some(
      (permission) =>
        permission.entity === entity &&
        permission.action === action &&
        (!scopes || scopes.includes(permission.scope)),
    );
  };
}

export function levelOf(roles: Pick<Role, "level">[] | null | undefined): number {
  return roles && roles.length > 0 ? Math.min(...roles.map((role) => role.level)) : LOWEST_LEVEL;
}

export function makeManage(session: Session): Manage {
  const superuser = session.roles.some((role) => role.superuser);
  const level = levelOf(session.roles);
  const myRoleIds = new Set(session.roles.map((role) => role.id));
  const myDepartmentIds = new Set((session.departments ?? []).map((department) => department.id));

  const holds = (entity: string, action: Action, target: ManageTarget | null) =>
    superuser ||
    session.permissions.some(
      (permission) =>
        permission.entity === entity &&
        permission.action === action &&
        (permission.scope === "all" ||
          (permission.scope === "department" && target !== null && (target.departments ?? []).some((department) => myDepartmentIds.has(department.id)))),
    );

  const relation = (target: ManageTarget): Relation => {
    if (target.id === session.user.id) return "self";
    const targetLevel = levelOf(target.roles);
    if (targetLevel < level) return "senior";
    if (targetLevel > level) return "junior";
    return (target.roles ?? []).some((role) => role.level === level && myRoleIds.has(role.id)) ? "same_role" : "parallel_role";
  };

  const roleRelation = (role: Pick<Role, "id" | "level">): Relation => {
    if (role.level < level) return "senior";
    if (role.level > level) return "junior";
    return myRoleIds.has(role.id) ? "same_role" : "parallel_role";
  };

  const view = (target: ManageTarget) => {
    if (superuser) return true;
    const kind = relation(target);
    if (kind === "self" || kind === "junior") return true;
    return holds(RELATION_ENTITY[kind], "read", target);
  };

  const user = (target: ManageTarget, action: Action) => {
    if (superuser) return true;
    const kind = relation(target);
    if (kind === "self" || kind === "junior") return true;
    if (kind === "senior") return false;
    return holds(RELATION_ENTITY[kind], "read", target) && holds(RELATION_ENTITY[kind], action, target);
  };

  const role = (target: ManagedRole, action: Action) => {
    if (superuser) return true;
    if (target.superuser) return false;
    const kind = roleRelation(target);
    if (kind === "junior") return true;
    if (kind === "senior" || kind === "self") return false;
    return holds(RELATION_ENTITY[kind], action, null);
  };

  const minLevelFor = (target: ManagedRole | null) => {
    if (superuser) return 1;
    const atOwnLevel = target && myRoleIds.has(target.id) ? holds("same_role_peers", "update", null) : holds("parallel_role_peers", target ? "update" : "write", null);
    return Math.max(1, atOwnLevel ? level : level + 1);
  };

  return {
    level,
    selfId: session.user.id,
    relation,
    roleRelation,
    view,
    user,
    rolesOf: (target, action) => superuser || (target.id !== session.user.id && user(target, action)),
    role,
    minLevelFor,
    minRoleLevel: minLevelFor(null),
  };
}

export function isStaffOf(can: Can): boolean {
  return can("complaints", "update", ["department", "all"]);
}

export function roleLabel(session: Session): string {
  const sorted = [...session.roles].sort((a, b) => a.level - b.level);
  return sorted[0]?.name.replaceAll("_", " ") ?? "member";
}

export function describeLevel(level: number): string {
  if (level === 0) return "System administrator";
  if (level <= 10) return "Leadership";
  if (level <= 30) return "Staff";
  if (level === LOWEST_LEVEL) return "No role";
  return "Member";
}
