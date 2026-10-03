import { useMemo } from "react";
import { getRouteApi } from "@tanstack/react-router";
import { UsersView } from "@/components/users/users-view";
import { UserDetail } from "@/components/users/user-detail";

const usersApi = getRouteApi("/app/users");
const userApi = getRouteApi("/app/users/$id");

export function UsersPage() {
  const search = usersApi.useSearch();
  const filters = useMemo(() => ({ page: 0, ...search }), [search]);
  return <UsersView filters={filters} />;
}

export function UserPage() {
  const { id } = userApi.useParams();
  return <UserDetail id={id} />;
}
