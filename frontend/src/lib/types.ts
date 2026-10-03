export type Scope = "own" | "department" | "all";
export type Action = "read" | "write" | "update" | "delete";

export type Permission = {
  id: string;
  entity: string;
  action: Action;
  scope: Scope;
  description?: string;
};

export type Role = {
  id: string;
  name: string;
  description?: string | null;
  level: number;
  system: boolean;
  superuser: boolean;
  revealIdentity: boolean;
  customFields?: Record<string, unknown>;
  createdAt?: string;
};

export type RoleWithPermissions = Role & { permissions: Permission[] };

export type Department = {
  id: string;
  name: string;
  code: string;
  description?: string | null;
  active: boolean;
  customFields?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string | null;
};

export type User = {
  id: string;
  email: string;
  name: string;
  enabled: boolean;
  verified: boolean;
  customFields?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string | null;
};

export type UserInfo = User & { roles: Role[]; departments: Department[] };

export type Session = { user: User; roles: RoleWithPermissions[]; permissions: Permission[]; departments: Department[] };

export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

export type ComplaintStatus = "pending" | "reviewed" | "resolved" | "rejected";
export type ComplaintPriority = "low" | "medium" | "high" | "urgent";

export type Complaint = {
  id: string;
  title: string;
  description: string;
  category: string;
  priority: ComplaintPriority;
  status: ComplaintStatus;
  studentId: string;
  studentName: string | null;
  subjects?: { id: string; name: string | null }[];
  assignedTo: string | null;
  assignedToName: string | null;
  departmentId: string | null;
  departmentName: string | null;
  resolution: string | null;
  resolvedAt: string | null;
  raisedBy: string | null;
  raisedByName: string | null;
  raisedByDepartments: string[] | null;
  reporterVisible: boolean;
  revealReporter: boolean;
  customFields?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string | null;
};

export type ComplaintComment = {
  id: string;
  complaintId: string;
  authorId: string | null;
  authorName: string | null;
  body: string;
  internal: boolean;
  createdAt: string;
};

export type ComplaintFile = {
  id: string;
  complaintId: string;
  uploadedBy: string | null;
  uploadedByName: string | null;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  kind: "attachment" | "evidence";
  status: "pending" | "uploaded";
  createdAt: string;
};

export type ComplaintHistory = {
  id: string;
  complaintId: string;
  actorId: string | null;
  actorName: string | null;
  fromStatus: ComplaintStatus | null;
  toStatus: ComplaintStatus;
  note: string | null;
  createdAt: string;
};

export type ComplaintStats = {
  total: number;
  pending: number;
  reviewed: number;
  resolved: number;
  rejected: number;
  unassigned: number;
  low: number;
  medium: number;
  high: number;
  urgent: number;
  open: number;
  assignedToMe: number;
  mine: number;
  overdue: number;
  criticalOpen: number;
  createdLast7Days: number;
  resolvedLast7Days: number;
  avgResolutionHours: number | null;
  trend: { day: string; created: number; resolved: number }[];
  departments: { name: string; total: number; open: number }[];
};

export type CustomFieldType = "text" | "number" | "bool" | "date" | "select" | "multi_select";
export type CustomFieldEntity = "users" | "departments" | "roles" | "complaints";

export type CustomFieldDefinition = {
  id: string;
  entity: CustomFieldEntity;
  key: string;
  label: string;
  helpText: string | null;
  dataType: CustomFieldType;
  required: boolean;
  options: string[] | null;
  defaultValue: unknown;
  minValue: number | null;
  maxValue: number | null;
  maxLength: number | null;
  pattern: string | null;
  appliesToRoleId: string | null;
  appliesToDepartmentId: string | null;
  sortOrder: number;
  active: boolean;
};

export type AppNotification = {
  id: string;
  userId: string;
  type: string;
  title: string;
  body: string | null;
  data: Record<string, string> | null;
  priority: "low" | "normal" | "high";
  source: string | null;
  readAt: string | null;
  createdAt: string;
};

export type FaceBoundingBox = { x: number; y: number; width: number; height: number };
export type FaceMatch = {
  box: FaceBoundingBox;
  confidence: number | null;
  matched: boolean;
  user: { id: string; name: string; email: string | null } | null;
};
export type FaceRecognizeResult = { image: string; width: number; height: number; faces: FaceMatch[] };
export type FaceEnrollment = {
  userId: string;
  name: string;
  email: string;
  enrolledAt: string;
  selfEnrollCount: number;
  selfEnrollLocked: boolean;
  canReplace: boolean | null;
  canRemove: boolean | null;
  canUnlock: boolean | null;
};

export type FaceAuditEvent = "recognize" | "live_recognize" | "enroll" | "replace" | "remove" | "unlock" | "bulk_import";
export type FaceAuditEntry = {
  id: string;
  event: FaceAuditEvent;
  actorId: string | null;
  actorName: string | null;
  subjectId: string | null;
  subjectName: string | null;
  matched: boolean | null;
  confidence: number | null;
  facesDetected: number | null;
  detail: string | null;
  createdAt: string;
};

export type ProcessStatus = "running" | "done" | "error";
export type ProcessState = {
  processId: string;
  kind: string;
  label: string;
  status: ProcessStatus;
  processed: number;
  total: number;
  messages: string[];
  updatedAt: number;
};

export type Device = { id: string; platform: string; deviceName: string | null; active: boolean; lastSeenAt: string; createdAt: string };
export type Preferences = { pushEnabled: boolean; realtimeEnabled: boolean; mutedTypes: string[] };
export type Integration = {
  provider: string;
  enabled: boolean;
  configured: boolean;
  config: { project_id?: string; client_email?: string };
  updatedAt: string | null;
};
export type Delivery = { id: string; notificationId: string | null; channel: string; status: string; detail: string | null; createdAt: string };
export type DeliveryStats = {
  realtimeSent: number;
  fcmSent: number;
  fcmFailed: number;
  skipped: number;
  activeDevices: number;
  notificationsLast24h: number;
};
