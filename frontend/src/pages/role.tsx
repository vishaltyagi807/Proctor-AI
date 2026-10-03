import { getRouteApi } from "@tanstack/react-router";
import { RoleDetail } from "@/components/roles/role-detail";

const roleApi = getRouteApi("/app/roles/$id");

export function RolePage() {
  const { id } = roleApi.useParams();
  return <RoleDetail id={id} />;
}
