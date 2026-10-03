import type { CustomFieldEntity } from "./types";


export const DASHBOARD_PATHS = {
  stats: "/complaints/stats",
  recent: "/complaints?size=6&sortBy=createdAt&direction=desc",
  notifications: "/notifications?size=5",
  users: "/users?size=1",
  departments: "/departments?size=1",
  roles: "/roles?size=1",
  queue: "/complaints?size=6&sortBy=createdAt&direction=asc&status=pending",
};

export const NOTIFICATION_PATHS = {
  preferences: "/notifications/preferences",
  devices: "/notifications/devices",
};

export const inboxPath = (page: number, unread: boolean) => `/notifications?size=12&page=${page}${unread ? "&unread=true" : ""}`;

export const detailPaths = (id: string) => ({
  complaint: `/complaints/${id}`,
  history: `/complaints/${id}/history`,
  comments: `/complaints/${id}/comments`,
  files: `/complaints/${id}/files`,
});

export const customFieldsPath = (entity: CustomFieldEntity) => `/custom-fields?entity=${entity}&size=100&sortBy=sortOrder&direction=asc`;

export const FACES_PATH = "/faces/enrollments";

export const ADMIN_PATHS = {
  integrations: "/notifications/admin/integrations",
  stats: "/notifications/admin/stats",
  deliveries: (page: number) => `/notifications/admin/deliveries?size=12&page=${page}`,
  people: "/users?size=100&sortBy=name&direction=asc",
};
