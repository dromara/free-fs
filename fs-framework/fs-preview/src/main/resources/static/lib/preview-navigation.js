(function () {
    const prefix = 'free-fs-preview-navigation:';
    const fileId = document.body.dataset.fileId || '';
    const sessionId = new URLSearchParams(window.location.search).get('navSession');
    const prevButton = document.getElementById('preview-prev');
    const nextButton = document.getElementById('preview-next');
    let busy = false;

    function readJson(key) {
        try {
            const value = localStorage.getItem(key);
            return value ? JSON.parse(value) : null;
        } catch (_) {
            return null;
        }
    }

    function getState() {
        if (!sessionId || !fileId) return null;
        const saved = readJson(prefix + sessionId);
        if (!saved || !Array.isArray(saved.ids)) return null;
        const index = saved.ids.indexOf(fileId);
        return index >= 0 ? {ids: saved.ids, index: index} : null;
    }

    function updateButtons() {
        const state = getState();
        if (prevButton) prevButton.disabled = !state || state.index <= 0;
        if (nextButton) nextButton.disabled = !state || state.index >= state.ids.length - 1;
    }

    window.navigatePreview = async function (offset) {
        if (busy) return;
        const state = getState();
        const targetId = state && state.ids[state.index + offset];
        if (!targetId) return;

        busy = true;
        if (prevButton) prevButton.disabled = true;
        if (nextButton) nextButton.disabled = true;
        try {
            const workspaceId = readJson('workspace-storage')?.state?.currentWorkspaceId;
            const storageId = readJson('current-storage-platform')?.settingId;
            const headers = {};
            if (workspaceId) headers['X-Workspace-Id'] = workspaceId;
            if (storageId) headers['X-Storage-Platform-Config-Id'] = storageId;

            const response = await fetch('/preview/token/' + encodeURIComponent(targetId), {
                method: 'POST',
                credentials: 'include',
                headers: headers
            });
            const result = await response.json();
            if (!response.ok || result.code !== 200 || !result.data) throw new Error('token failed');

            window.location.href = '/preview/' + encodeURIComponent(targetId)
                + '?previewToken=' + encodeURIComponent(result.data)
                + '&navSession=' + encodeURIComponent(sessionId);
        } catch (_) {
            busy = false;
            updateButtons();
        }
    };

    document.addEventListener('keydown', function (event) {
        if (event.shiftKey || event.ctrlKey || event.altKey || event.metaKey) return;
        if (event.key === 'ArrowLeft') {
            event.preventDefault();
            window.navigatePreview(-1);
        } else if (event.key === 'ArrowRight') {
            event.preventDefault();
            window.navigatePreview(1);
        }
    });

    updateButtons();
})();
