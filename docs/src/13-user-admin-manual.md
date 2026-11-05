---
title: User & Administrator Manual
subtitle: How students, staff and administrators use ProctorAI on the web, desktop and mobile.
id: PAI-USR-013
version: 1.0
category: User guide
audience: Students, staff, administrators and support desks
classification: Public
pdf: 13-ProctorAI-User-and-Administrator-Manual
related: SRS (PAI-SRS-001), Security & Hardening (PAI-SEC-005)
---

## About ProctorAI

ProctorAI helps an institution handle complaints, manage people and access, and identify people with on-premises face recognition. Everything you can see and do depends on your **role**. If a menu item or button described here is missing, your role does not include that permission; ask an administrator.

| You are… | You typically can |
|---|---|
| Student / member | File complaints, follow their progress, comment, attach files, manage your notifications and (if allowed) enroll your face once |
| Staff / teacher | Everything above, plus review, assign and resolve complaints of your departments, see department users, and use face recognition if granted |
| Administrator | Manage users, departments, roles, custom fields, notification settings and the system monitor; full access to every complaint |

## Getting started

### Supported apps

| App | How to get it |
|---|---|
| Web | Open the address provided by your institution, for example `https://app.example.com`, in Chrome, Edge, Firefox or Safari. On phones you can choose **Add to Home Screen** |
| Windows, macOS, Linux | Install the package provided by your institution |
| Android | Install the APK or the app from your institution's store listing |
| iOS | Install through TestFlight or the App Store |

Native apps may ask for the **server address** on first launch. Enter the web address of your institution (it must start with `https://`). The app checks it before saving.

### Signing in

1. Enter your email and password, then select **Sign in**.
2. After repeated wrong passwords, sign-in is paused for your account for a short period (15 minutes by default). Wait and try again, or contact an administrator.
3. Your session renews automatically while you use the app. Select **Sign out** from your profile menu when using a shared device.

### Layout

| Area | Purpose |
|---|---|
| Sidebar | Main navigation: Overview, Complaints, Users, Departments, Roles & access, Custom fields, Face recognition, Notifications, Broadcast & push, System monitor |
| Bell icon | Unread notification count and recent notifications |
| Profile menu | My profile, theme (Light, Dark, System), Sign out |
| Command palette | Press **Ctrl + K** (**⌘ + K** on macOS) to search pages and actions |

## Overview dashboard

The **Overview** page summarises complaints you can see:

| Widget | Shows |
|---|---|
| Totals | All, open, pending, in review, resolved, rejected |
| My work | Assigned to me, filed by me |
| Attention | Overdue and critical open complaints, unassigned complaints |
| Trend | Complaints created and resolved over the last 7 days |
| Priority | Distribution across low, medium, high and urgent |
| Departments | Total and open complaints per department |
| Resolution time | Average hours to resolve |

## Complaints

### Filing a complaint

1. Select **New complaint**.
2. Enter a **title** (up to 200 characters) and a clear **description** (up to 5000).
3. Choose a **category** and **priority** (low, medium, high or urgent). Use *urgent* only for safety or time-critical issues.
4. Choose the **department** responsible, if offered.
5. Optionally add the people the complaint **concerns**. Staff can also identify people from a photo with face recognition.
6. Fill in any additional fields your institution requires.
7. Attach photos or documents by dragging them onto the drop zone.
8. Select **Submit**.

The complaint starts as **Pending**. You receive notifications as it progresses.

### Complaint statuses

| Status | Meaning |
|---|---|
| Pending | Filed and waiting for staff. You can still edit it |
| In review | Staff are working on it. It can no longer be edited by the reporter |
| Resolved | Closed with a resolution message |
| Rejected | Closed without action, with a reason |

### Following your complaint

Open **Complaints** and select a complaint to see:

- the details, department, assignee and priority;
- **Activity / history** with every status change and its note;
- **Comments**, where you can add information or answer questions;
- **Files**, with preview and download.

You cannot edit a complaint after staff have started processing it. Add a comment instead.

### Reporter privacy

By default your identity as the reporter is hidden from people who do not need it. Only roles that are allowed to reveal identities, or complaints explicitly marked to reveal the reporter, show your name.

### Processing complaints (staff)

1. Filter **Complaints** by status, priority, department or assignee. Use the search box to find text in titles and descriptions.
2. Open a complaint and select **Assign** to choose the handler.
3. Change the status to **In review** when you start working on it, adding a **note for the history**.
4. Use **internal comments** for discussion among staff; reporters and subjects cannot see them.
5. When finished, set the status to **Resolved** with a **resolution message**, or **Rejected** with the reason.
6. Use **Edit complaint** to correct the category, priority or department if needed.

> [!TIP]
> Every status change notifies the reporter and the people involved. A short, clear note in the history saves follow-up questions.

### Attachments

| Rule | Value |
|---|---|
| Maximum size | 250 MB per file |
| Upload | Shows progress; keep the page open until it completes |
| Download | Desktop apps open a save dialog; phones open the system viewer |
| Delete | Allowed for the uploader or staff with permission |

## Notifications

| Where | What you get |
|---|---|
| In the app | Real-time pop-ups and the notification inbox (**Notifications**) |
| Desktop and mobile apps | System notifications while the app is in the background |
| Mobile push | When your institution has enabled push and you allow notifications |

In **Notifications** you can mark items as read, mark all as read and delete items. Under **Delivery preferences** (*Choose how you get notified*) you can turn real-time or push notifications off, **Mute these types**, and review or remove **Your devices**.

## Face recognition

Face recognition runs entirely on your institution's server. Photos are not sent to any external service.

### Enrolling your face

1. Open **Face recognition** and choose **Enroll**.
2. Take a photo with the camera or upload one. Use good front lighting, look straight at the camera, remove sunglasses or masks, and make sure only your face is in the frame.
3. Confirm. Your enrollment appears under **Enrolled people**.

> [!IMPORTANT]
> You can enroll yourself only once by default. To replace your enrollment, ask a staff member or administrator who manages enrollments to re-enroll or unlock you.

### Recognising people (authorised staff)

| Mode | How |
|---|---|
| Photo | Upload an image; every detected face is boxed and labelled with the matched person or **Unknown** |
| Live camera | Start **Live camera**; faces are recognised continuously from the device camera |

A person is shown only if their face is enrolled, the similarity is above the institution's threshold, and you are allowed to see that person. Otherwise the face is labelled **Unknown**.

### Activity

The activity panel lists recognitions, enrollments, replacements, removals and unlocks with who did them and when. Students can see activity that concerns them.

### Good practice

- Use recognition only for the purposes your institution has approved.
- Treat results as assistance, not proof. Confirm identity by other means before acting.
- Do not save or share annotated images outside ProctorAI.

## Your profile

Open **My profile** from the profile menu to review your name, email, roles and departments, and to choose the theme. To change your password, open **Users**, select your own account, choose **Edit**, enter a **New password** (at least 8 characters) and save. If you cannot open Users, ask an administrator to set a new password for you.

## Administration

### Users

| Task | Steps |
|---|---|
| Create a user | **Users → New**. Enter name, email, initial password (8+ characters), roles, departments and custom fields |
| Edit or disable | Open the user → **Edit**. Disabling a user signs them out everywhere |
| Reset a password | Edit the user → **New password** |
| Bulk import | **Users → Import**. Download the CSV or Excel template, fill in `name, email, password, roles, departments, enabled, verified`, upload, and watch the progress. Fix rows reported with errors and import them again |
| Bulk delete | Select users in the table → **Delete** |
| Find users | Search and filter by department, role and verification; choose which columns the table shows, including custom fields |

Rules enforced by the system:

- You can manage only yourself and people more junior than you, unless you are a system administrator.
- You cannot change your own roles or disable your own account.
- The last system administrator cannot be removed, disabled or deleted.

### Departments

1. **Departments → New**: enter a name and a unique code (for example `CS`).
2. Open a department to add or remove **members** in bulk.
3. Department membership decides what *department-scoped* permissions cover, for example which complaints a teacher sees.

### Roles & access

A role has a **level** and a set of **permissions**. Lower levels are more senior (0 is reserved for system administrators).

| Default role | Level | Summary |
|---|---|---|
| `system_admin` | 0 | Unrestricted; cannot be deleted |
| `admin` | 0 | Unrestricted; cannot be deleted |
| `teacher` | 20 | Department-scoped complaint handling, face recognition, department users |
| `student` | 50 | Own complaints, own profile, own notifications, one self-enrollment |

The permission matrix lists every area (Users, Complaints, Files, Face recognition, Live camera, Enrollments, Enrollment lock, System monitor…) against the actions **read, write (create), update, delete** and the scopes:

| Scope | Covers |
|---|---|
| Own | Your own records |
| Department | Records linked to your departments |
| All | Everything |

To create a role: **Roles & access → New**, set name, description and level, tick permissions, and save. You can only grant permissions you hold yourself. Tick **Reveal identity** only for roles that must see complaint reporters.

### Custom fields

Add institution-specific information without software changes.

1. **Custom fields → New**.
2. Choose where it applies: users, departments, roles or complaints.
3. Enter a key (lower case, for example `hostel_block`), a label and help text.
4. Choose the type: text, number, yes/no, date, single choice or multiple choice.
5. Set validation (required, options, minimum/maximum, maximum length, pattern) and optionally limit it to one role or department.
6. Save. The field appears in the forms and tables immediately.

The key and type cannot be changed later; create a new field instead. Deleting a field deletes its stored values.

### Broadcast & push

| Task | Steps |
|---|---|
| Send an announcement | **Broadcast & push → Compose**. Choose individual people, roles and/or departments, enter a title and message, choose priority (low, normal, high), optionally **Include me**, then **Send message** |
| Enable mobile push | **Push integration → Firebase Cloud Messaging**: follow **How to connect**, paste the Firebase service account JSON, enable, then test |
| Check delivery | **Delivery log** / **Recent deliveries** shows each attempt per channel (Realtime, Push) as sent, failed or skipped with details, plus totals and active devices |

### System monitor

**System monitor** shows the live state of the server: CPU, memory, disks and network; each service with its status and resource use; database and cache health; storage usage; and a list of detected **issues**. Report persistent issues to your technical operator.

## Troubleshooting

| Problem | What to do |
|---|---|
| "Wrong email or password" | Check caps lock and the email address; after several attempts wait 15 minutes |
| Signed out unexpectedly | Your session expired or an administrator signed you out; sign in again |
| A menu item is missing | Your role does not include that permission |
| "Complaint is already being processed" | Staff have started work; add a comment instead of editing |
| Upload stops | Check the connection and file size (max 250 MB); keep the page open |
| No real-time notifications | Reload the page; check notification settings; some networks block live connections |
| Face not recognised | Re-take the photo with better light and a straight view; ask staff to re-enroll you if needed |
| App cannot reach the server | Check the server address on the sign-in screen; it must be the institution's `https://` address |

## Glossary

| Term | Meaning |
|---|---|
| Assignee | Staff member responsible for a complaint |
| Department scope | Permission limited to records linked to your departments |
| Enrollment | Your stored face template used for recognition |
| Internal comment | Comment visible only to staff |
| Level | Seniority of a role; lower is more senior |
| Reporter | The person who filed a complaint |
| Subject | A person a complaint is about |
