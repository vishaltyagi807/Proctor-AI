(function () {
    'use strict';

    const $ = (id) => document.getElementById(id);
    let events = null;

    async function api(path, options) {
        const response = await fetch(path, Object.assign({
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' }
        }, options || {}));
        let body = null;
        const text = await response.text();
        if (text) { try { body = JSON.parse(text); } catch (e) { body = text; } }
        if (!response.ok) {
            const error = new Error((body && body.message) || ('HTTP ' + response.status));
            error.status = response.status;
            throw error;
        }
        return body;
    }

    function show(id, message, ok) {
        const element = $(id);
        element.textContent = message || '';
        element.className = 'msg ' + (ok ? 'ok' : 'bad');
    }

    function pill(text, kind) {
        const span = document.createElement('span');
        span.className = 'pill ' + kind;
        span.textContent = text;
        return span;
    }

    async function boot() {
        try {
            const me = await api('/users/me');
            $('session').textContent = me.name + ' (' + me.email + ')';
            $('login').classList.add('hidden');
            $('app').classList.remove('hidden');
            $('session').dataset.userId = me.id;
            await Promise.all([loadStats(), loadIntegration(), loadDeliveries(), loadTargets()]);
        } catch (e) {
            $('session').textContent = 'Not signed in';
            $('app').classList.add('hidden');
            $('login').classList.remove('hidden');
        }
    }

    async function loadTargets() {
        const fill = async (path, id) => {
            const select = $(id);
            select.replaceChildren();
            try {
                const page = await api(path);
                page.content.forEach((item) => {
                    const option = document.createElement('option');
                    option.value = item.id;
                    option.textContent = item.name;
                    select.append(option);
                });
            } catch (e) { select.disabled = true; }
        };
        await Promise.all([fill('/roles?size=100&sortBy=name&direction=asc', 'sendRoles'),
            fill('/departments?size=100&sortBy=name&direction=asc', 'sendDepartments')]);
    }

    function selected(id) {
        return Array.from($(id).selectedOptions).map((option) => option.value);
    }

    async function loadStats() {
        const container = $('stats');
        container.replaceChildren();
        try {
            const stats = await api('/notifications/admin/stats');
            const items = [
                ['Notifications', stats.notificationsLast24h], ['Realtime sent', stats.realtimeSent],
                ['Push sent', stats.fcmSent], ['Push failed', stats.fcmFailed],
                ['Skipped', stats.skipped], ['Active devices', stats.activeDevices]
            ];
            items.forEach(([label, value]) => {
                const box = document.createElement('div');
                box.className = 'stat';
                const strong = document.createElement('b');
                strong.textContent = value;
                const span = document.createElement('span');
                span.textContent = label;
                box.append(strong, span);
                container.append(box);
            });
        } catch (e) {
            container.textContent = e.message;
        }
    }

    async function loadIntegration() {
        const status = $('fcmStatus');
        status.replaceChildren();
        try {
            const list = await api('/notifications/admin/integrations');
            const fcm = list.find((i) => i.provider === 'fcm');
            if (!fcm) {
                status.append(pill('not configured', 'warn'));
                $('fcmEnabled').value = 'false';
                return;
            }
            status.append(pill(fcm.enabled ? 'enabled' : 'disabled', fcm.enabled ? 'ok' : 'warn'), ' ');
            status.append(pill(fcm.configured ? 'credentials saved' : 'no credentials', fcm.configured ? 'ok' : 'bad'));
            if (fcm.config && fcm.config.project_id) {
                const info = document.createElement('div');
                info.className = 'muted';
                info.textContent = 'Project: ' + fcm.config.project_id + ' · ' + (fcm.config.client_email || '');
                status.append(info);
            }
            $('fcmEnabled').value = String(fcm.enabled);
        } catch (e) {
            status.textContent = e.message;
        }
    }

    async function loadDeliveries() {
        const body = $('deliveries');
        body.replaceChildren();
        try {
            const page = await api('/notifications/admin/deliveries?size=15');
            page.content.forEach((d) => {
                const row = document.createElement('tr');
                [new Date(d.createdAt).toLocaleTimeString(), d.channel, d.status, d.detail || ''].forEach((value) => {
                    const cell = document.createElement('td');
                    cell.textContent = value;
                    row.append(cell);
                });
                body.append(row);
            });
        } catch (e) {
            const row = document.createElement('tr');
            const cell = document.createElement('td');
            cell.colSpan = 4;
            cell.textContent = e.message;
            row.append(cell);
            body.append(row);
        }
    }

    $('loginForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        try {
            await api('/auth/login', { method: 'POST', body: JSON.stringify({ email: $('email').value, password: $('password').value }) });
            $('password').value = '';
            await boot();
        } catch (e) {
            show('loginMsg', e.message, false);
        }
    });

    $('fcmSave').addEventListener('click', async () => {
        try {
            const payload = { enabled: $('fcmEnabled').value === 'true' };
            const json = $('fcmJson').value.trim();
            if (json) { payload.serviceAccountJson = json; }
            await api('/notifications/admin/integrations/fcm', { method: 'PUT', body: JSON.stringify(payload) });
            $('fcmJson').value = '';
            show('fcmMsg', 'Saved. Changes apply within 30 seconds on every instance.', true);
            await loadIntegration();
        } catch (e) {
            show('fcmMsg', e.message, false);
        }
    });

    $('fcmTest').addEventListener('click', async () => {
        try {
            const result = await api('/notifications/admin/integrations/fcm/test', { method: 'POST', body: '{}' });
            const ok = result.credentialsValid && result.failed === 0;
            show('fcmMsg', (result.credentialsValid ? 'Credentials valid. ' : '') + 'Devices: ' + result.devicesTried
                + ', sent: ' + result.sent + ', failed: ' + result.failed + '. ' + (result.detail || ''), ok);
        } catch (e) {
            show('fcmMsg', e.message, false);
        }
    });

    $('fcmClear').addEventListener('click', async () => {
        try {
            await api('/notifications/admin/integrations/fcm', { method: 'DELETE' });
            show('fcmMsg', 'FCM credentials removed and push disabled.', true);
            await loadIntegration();
        } catch (e) {
            show('fcmMsg', e.message, false);
        }
    });

    $('sendBtn').addEventListener('click', async () => {
        try {
            const raw = $('sendUsers').value.split(',').map((v) => v.trim()).filter(Boolean);
            const roleIds = selected('sendRoles');
            const departmentIds = selected('sendDepartments');
            const none = !raw.length && !roleIds.length && !departmentIds.length;
            const userIds = none ? [$('session').dataset.userId] : raw;
            const result = await api('/notifications/send', {
                method: 'POST',
                body: JSON.stringify({ userIds, roleIds, departmentIds, type: $('sendType').value, title: $('sendTitle').value, body: $('sendBody').value, priority: $('sendPriority').value })
            });
            show('sendMsg', 'Queued for ' + result.recipients + ' recipient(s).', true);
            setTimeout(() => { loadDeliveries(); loadStats(); }, 1200);
        } catch (e) {
            show('sendMsg', e.message, false);
        }
    });

    $('refreshDeliveries').addEventListener('click', () => { loadDeliveries(); loadStats(); });

    $('liveBtn').addEventListener('click', () => {
        const state = $('liveState');
        if (events) {
            events.close();
            events = null;
            state.textContent = 'disconnected';
            state.className = 'pill';
            $('liveBtn').textContent = 'Connect';
            return;
        }
        events = new EventSource('/notifications/stream', { withCredentials: true });
        events.onopen = () => { state.textContent = 'connected'; state.className = 'pill ok'; $('liveBtn').textContent = 'Disconnect'; };
        events.onerror = () => { state.textContent = 'reconnecting'; state.className = 'pill warn'; };
        events.addEventListener('notification', (message) => {
            let title = message.data;
            try { const n = JSON.parse(message.data); title = n.title + (n.body ? ' — ' + n.body : ''); } catch (e) { title = message.data; }
            const item = document.createElement('li');
            item.textContent = new Date().toLocaleTimeString() + '  ' + title;
            $('live').prepend(item);
        });
    });

    boot();
}());
