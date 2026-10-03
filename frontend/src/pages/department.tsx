import { getRouteApi } from "@tanstack/react-router";
import { DepartmentDetail } from "@/components/departments/department-detail";

const departmentApi = getRouteApi("/app/departments/$id");

export function DepartmentPage() {
  const { id } = departmentApi.useParams();
  return <DepartmentDetail id={id} />;
}
