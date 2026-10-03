import { getRouteApi } from "@tanstack/react-router";
import { ComplaintsView } from "@/components/complaints/complaints-view";
import { ComplaintDetail } from "@/components/complaints/complaint-detail";

const complaintsApi = getRouteApi("/app/complaints");
const complaintApi = getRouteApi("/app/complaints/$id");

export function ComplaintsPage() {
  const search = complaintsApi.useSearch();
  return <ComplaintsView filters={{ page: 0, ...search }} />;
}

export function ComplaintPage() {
  const { id } = complaintApi.useParams();
  return <ComplaintDetail id={id} />;
}
